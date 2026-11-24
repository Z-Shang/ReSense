package dev.sense.messages;

import dev.sense.messages.mms.pdu.*;

/** Legacy parser assertions are validation failures at the untrusted carrier boundary. */
final class MmsCodec {
    static GenericPdu parse(byte[] bytes){if(bytes==null||bytes.length==0||bytes.length>MmsFiles.MAX_BYTES)return null;try{return new PduParser(bytes,true).parse();}catch(RuntimeException|AssertionError malformed){return null;}}
}
