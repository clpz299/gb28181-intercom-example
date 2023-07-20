package com.blue.butterball.gb28181.codec;

public class SilenceCodec extends AudioCodec {
    static final byte[] BLANK = new byte[0];

    @Override
    public byte[] toPCM(byte[] data) {
        return BLANK;
    }

    @Override
    public byte[] fromPCM(byte[] data) {
        return BLANK;
    }
}
