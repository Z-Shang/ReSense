"""Read-only Mode10 v2 subset recovered from the supplied Pyramid ROM.

Records: LE u32 (payload length << 4 | kind), LE u32 hashed tag, payload,
padded to four bytes. Kind 1 contains records; kind 2 contains bytes.
Hashes are DJB2 with the high bit cleared. This is not a general renderer.
"""
from dataclasses import dataclass
import struct
import zlib

MAX_BYTES = 64 * 1024 * 1024


def name_hash(text):
    value = 5381
    for char in text:
        value = (value * 33 + ord(char)) & 0x7fffffff
    return value


@dataclass
class Record:
    offset: int
    tag: int
    kind: int
    payload: memoryview
    children: list


def parse(data):
    if len(data) < 16 or len(data) > MAX_BYTES or data[:8] != b'!01m\x02\x00\x00\x00':
        raise ValueError('Expected bounded Mode10 v2 input')
    view = memoryview(data)
    count = 0

    def records(start, end, depth):
        nonlocal count
        if depth > 128:
            raise ValueError('Excessive record nesting')
        result = []
        while start < end:
            if start + 8 > end:
                raise ValueError(f'Truncated record at {start:#x}')
            word, tag = struct.unpack_from('<II', view, start)
            length, kind = word >> 4, word & 15
            stop = start + 8 + length
            aligned = start + 8 + ((length + 3) & ~3)
            count += 1
            if kind not in (1, 2) or aligned > end or count > 200_000:
                raise ValueError(f'Unsupported or out-of-bounds record at {start:#x}')
            children = records(start + 8, stop, depth + 1) if kind == 1 else []
            result.append(Record(start, tag, kind, view[start + 8:stop], children))
            start = aligned
        return result

    roots = records(8, len(data), 0)
    if len(roots) != 1 or roots[0].tag != name_hash('Root') or roots[0].kind != 1:
        raise ValueError('Expected one Root container')
    return roots[0]


def walk(record):
    yield record
    for child in record.children:
        yield from walk(child)


def properties(record):
    result = {}
    for child in record.children:
        if child.tag != name_hash('Property') or child.kind != 1:
            continue
        if len(child.children) != 2:
            raise ValueError('Unsupported property record')
        key, value = child.children
        if key.tag != name_hash('Name') or key.kind != 2 or len(key.payload) != 4:
            raise ValueError('Expected property name hash')
        result[struct.unpack('<I', key.payload)[0]] = value
    return result


def integer(record):
    if record.kind != 2 or len(record.payload) != 4:
        raise ValueError('Expected u32')
    return struct.unpack('<I', record.payload)[0]


def text(record):
    if record.kind != 2 or len(record.payload) % 2:
        raise ValueError('Expected UTF-16LE')
    return bytes(record.payload).decode('utf-16le').rstrip('\0')


def fastlz(data, expected):
    """Bounded FastLZ level 1/2 decoder; expected size comes from the M10 record."""
    if not data or not 0 < expected <= MAX_BYTES:
        raise ValueError('Invalid FastLZ input/size')
    level = (data[0] >> 5) + 1
    if level not in (1, 2):
        raise ValueError('Unsupported FastLZ level')
    position, control = 1, data[0] & 31
    output = bytearray()

    def byte():
        nonlocal position
        if position >= len(data):
            raise ValueError('Truncated FastLZ input')
        value = data[position]
        position += 1
        return value

    while True:
        if control < 32:
            length = control + 1
            if position + length > len(data) or len(output) + length > expected:
                raise ValueError('Invalid FastLZ literal')
            output.extend(data[position:position + length])
            position += length
        else:
            length, high = (control >> 5) - 1, (control & 31) << 8
            if length == 6:
                while True:
                    extra = byte()
                    length += extra
                    if level == 1 or extra != 255:
                        break
            low = byte()
            distance = high + low + 1
            if level == 2 and low == 255 and high == 31 << 8:
                distance = (byte() << 8) + byte() + 8192
            length += 3
            if distance > len(output) or len(output) + length > expected:
                raise ValueError('Invalid FastLZ backreference')
            for _ in range(length):
                output.append(output[-distance])
        if position == len(data):
            break
        control = byte()
    if len(output) != expected:
        raise ValueError('FastLZ decompressed size mismatch')
    return bytes(output)


MODIFIERS = ((2, 8, -2, -8), (5, 17, -5, -17), (9, 29, -9, -29),
             (13, 42, -13, -42), (18, 60, -18, -60), (24, 80, -24, -80),
             (33, 106, -33, -106), (47, 183, -47, -183))


def etc1(data, width, height):
    """Decode standard ETC1 blocks (Khronos OES_compressed_ETC1_RGB8_texture)."""
    if width <= 0 or height <= 0 or width * height * 3 > MAX_BYTES:
        raise ValueError('Invalid ETC1 dimensions')
    expected = ((width + 3) // 4) * ((height + 3) // 4) * 8
    if len(data) != expected:
        raise ValueError('ETC1 block count mismatch')
    output = bytearray(width * height * 3)
    position = 0
    for by in range(0, height, 4):
        for bx in range(0, width, 4):
            high, low = struct.unpack_from('>II', data, position)
            position += 8
            if high & 2:
                first = [(high >> shift) & 31 for shift in (27, 19, 11)]
                deltas = [(high >> shift) & 7 for shift in (24, 16, 8)]
                second = [v + (d if d < 4 else d - 8) for v, d in zip(first, deltas)]
                if not all(0 <= c <= 31 for c in second):
                    raise ValueError('Invalid ETC1 differential block')
                colors = [[(c << 3) | (c >> 2) for c in values] for values in (first, second)]
            else:
                colors = [[((high >> shift) & 15) * 17 for shift in shifts]
                          for shifts in ((28, 20, 12), (24, 16, 8))]
            tables = [(high >> 5) & 7, (high >> 2) & 7]
            for y in range(min(4, height - by)):
                for x in range(min(4, width - bx)):
                    sub = int(y >= 2) if high & 1 else int(x >= 2)
                    bit = x * 4 + y
                    selector = ((low >> bit) & 1) | (((low >> (bit + 16)) & 1) << 1)
                    delta = MODIFIERS[tables[sub]][selector]
                    rgb = bytes(max(0, min(255, c + delta)) for c in colors[sub])
                    offset = ((by + y) * width + bx + x) * 3
                    output[offset:offset + 3] = rgb
    return bytes(output)


def png(path, width, height, pixels, alpha=False):
    channels = 4 if alpha else 3
    if len(pixels) != width * height * channels:
        raise ValueError('PNG dimensions do not match pixels')

    def chunk(kind, payload):
        return (struct.pack('>I', len(payload)) + kind + payload
                + struct.pack('>I', zlib.crc32(kind + payload) & 0xffffffff))

    rows = b''.join(b'\0' + pixels[y * width * channels:(y + 1) * width * channels]
                    for y in range(height))
    path.write_bytes(b'\x89PNG\r\n\x1a\n'
                     + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6 if alpha else 2, 0, 0, 0))
                     + chunk(b'IDAT', zlib.compress(rows)) + chunk(b'IEND', b''))
