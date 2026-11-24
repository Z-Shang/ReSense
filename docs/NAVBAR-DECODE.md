# Navbar geometry and texture recovered from Mode10

## Result

The default navbar background in this supplied ROM is a **flat 540 × 90 rectangle**, not an arc. Its sprite has four vertices at `(0,0,0)`, `(540,0,0)`, `(540,90,0)` and `(0,90,0)`, paired with ordinary unit-square UVs. Its recovered texture also has a straight silhouette: the decoded alpha is nonzero across the whole image (range 99–206). No curved edge was recovered from this background.

This does not establish which revision or skin the user remembers, or rule out every other asset in the ROM. BlueSky and Burgundy shared texture libraries were also inspected: neither has an override for this background ID. A subsequent investigation with the supplied photo found a curved drawable and a retained three-button XML layout elsewhere in Rosie; see [DOCK-LAYERS.md](DOCK-LAYERS.md). The panel resources for that layout are transparent stubs in this ROM, and no binding over the Mode10 bar has yet been established. The current visual reconstruction bends a cleaned strip of this background using the recovered curve; see [DOCK-RENDERING.md](DOCK-RENDERING.md).

## Reproduce

Only Python’s standard library and the supplied ROM ZIP are needed:

```sh
python3 tools/decode_navbar.py
python3 -m unittest discover -s tools -p 'test_mode10.py' -v
```

Read-only ZIP access preserves the source archive. Generated original assets stay in ignored `references/3.33.401.153/navbar-decoded/`:

- `navbar.json`: 101 nodes, parent relationships, byte offsets, static local transforms, raw interpolation codes and sampled animation vectors.
- `navbar-geometry.svg`: wireframe of the recovered background quad.
- `home_nav_bg.png`: color plus alpha at the original 540 × 90 logical size.
- `home_nav_bg-color.png` and `home_nav_bg-alpha.png`: the individual decoded planes.
- `home_nav_bg-planes.etc1`: decompressed compressed-texture planes, including the 92-pixel padded height.
- `validation.json`: local native-codec comparison result from this investigation.

The CLI reads Rosie and Fusion directly from `res/3.33.401.153.zip`; it does not depend on manually extracted runtime libraries.

## Binary structure

The observed v2 files begin with `!01m` and little-endian version 2. Each record contains a little-endian length/kind word, a little-endian hashed tag, then a payload with four-byte alignment. The upper 28 bits of the first word give payload length; the low nibble is 1 for nested records or 2 for a byte payload. The navbar’s record tree traverses exactly to the end of the file: 8,370 records, including 101 scene nodes.

Tags and property identifiers match DJB2 hashes, starting at 5381, multiplying by 33 and adding each character, with the high bit cleared. Checks include `Root = 0x7c8bed89`, `Node = 0x7c89ba8b`, `Property = 0x22c58b2a` and `Position = 0x44b4de9a`. These are independently consistent with the names present in the supplied native library. A property pairs a Name hash with a Value record. Supported values include UTF-16LE names, float vectors, integer dimensions, sprite vertices and sampled controllers.

This remains a subset decoder. It leaves unknown fields uninterpreted and preserves interpolation codes rather than claiming complete animation semantics. Parent transforms and sampled animation values are reported, not evaluated into a full rendered scene.

## Geometry evidence

Source: `system/app/Rosie.apk!/assets/Port/scenes/Rosie_navbar.m10`.

Scene SHA-256: `ceb01a21b30831235a39776509b9e632d2471505a10f491289d3089e3bb282ef`.

| Node | Node record offset | Local position | Pivot position |
| --- | --- | --- | --- |
| `__Rosie_NavBar` | `0x3a0` | `[0.0, 0.0, 0.0]` | `[0.0, 0.0, 0.0]` |
| `timeline.NavBar_main` | `0xef4` | `[270.0, 480.0, -0.0]` | `[-270.0, -480.0, 0.0]` |
| `timeline.NavBar_phone_bg` | `0x13710` | `[270.0, 916.0, 0.0]` (frame 0) | `[-270.0, -45.0, -0.0]` |
| `home_nav_bg.png` | `0x14100` | `[270.0, 45.0, 0.0]` | `[-270.0, -45.0, -0.0]` |

For `home_nav_bg.png`, the `Vertices` value record starts at `0x14458`; its payload is 80 bytes, four groups of five float32 values interpreted as x/y/z/u/v. The `Size` value is `[540,90]`; rotation is `[0,0,0,1]` and scale is `[1,1,1]`. There is no tessellated curved mesh in this sprite. The parent background timeline contains movement samples; those describe placement/movement rather than a curved outline.

## Texture recovery

The sprite’s `DiffuseMap` value is `0x7ac752cd`, also the hash of `home_nav_bg.png`. This matches a texture ID in `system/app/fusion.apk!/assets/Default/drawable/shared.m10`, record offset `0x8f55f0`. The texture has Width 540, Height 90, Format 14, and one level described by `[540,92,0,24840]`.

Shared texture-library SHA-256: `0bcbd267bc41a32800de26cd48e040c4da94451c1f64fadfdb852f6974c41d39`.

The 1,359-byte Pixels payload decompresses to 49,680 bytes. Disassembly of the supplied `libmode10.so` shows `Texture::DecompressPixel` calling `DataCompressor::Decompress`; the supplied `liblz.so` implementation calls `fastlz_decompress`. A bounded Python FastLZ decoder recovers two 24,840-byte ETC1 planes. The GLES library contains ETC1 uploads and an alpha-texture path. Decoding the first plane as RGB and the second as grayscale alpha gives the recovered bar; all three channels of the alpha plane are identical.

The [ETC1 extension specification](https://registry.khronos.org/OpenGL/extensions/OES/OES_compressed_ETC1_RGB8_texture.txt) defines the block layout. [FastLZ’s reference implementation](https://github.com/ariya/FastLZ/blob/master/fastlz.c) documents the stream algorithm. No HTC binary is executed on the host or bundled into the launcher.

## Validation

- Eight unit tests cover hash/record parsing, alignment/truncation, unknown kinds, nesting limits, FastLZ overlap/extension/far-distance cases and corrupt references, plus ETC1 solid blocks, cropping and invalid inputs.
- Parsed all 199 extracted scene files plus the default shared library without boundary overruns; also parsed both alternate skin libraries.
- Ran `android.opengl.ETC1.decodeImage` for both recovered planes on the Android 15 emulator. The Python decoder matches all **298,080 output RGB bytes exactly**, including padded rows. SHA-256: `614992f9655b1025e94ad4600ca8330a573704c7b7a8027cb609695a15f19895`.

To repeat that comparison, boot the emulator, run the extraction above, then:

```sh
tools/verify_navbar_etc1.sh emulator-5554
```

This helper checks an explicitly selected emulator, compiles the small Java reference driver, pushes it into `/data/local/tmp`, invokes the platform ETC1 decoder and compares bytes locally. It does not install or alter the launcher. The temporary headless emulator used for this check was stopped afterward.

Pixel-codec equivalence validates decoding of the planes. It does not validate a full original Sense frame, all timeline interpolation, every texture format, or skin/resource selection at runtime. No launcher Java source or APK was changed in this investigation.
