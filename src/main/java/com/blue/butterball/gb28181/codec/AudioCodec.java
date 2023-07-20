package com.blue.butterball.gb28181.codec;


public abstract class AudioCodec {
    public abstract byte[] toPCM(byte[] data);

    public abstract byte[] fromPCM(byte[] data);

    public static AudioCodec getCodec(int encoding) {
        if (1 == encoding) return new G711Codec();
        else return new SilenceCodec();
    }
}