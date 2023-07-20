package com.blue.butterball.gb28181.netty;


import cn.hutool.core.util.ByteUtil;

public class RtpPack {

    private byte[] rtpHeader = new byte[12];
    private short currentSeqNo = 0;
    private byte[] g711A = new byte[696];
    private int payload = 0;
    private int ssrc = 0;
    private int sampleRate = 8000;
    private int currentTimestamp = 0;
    private int duration = 8000 / 25;

    public static byte[] intToBytes(int val) {
        byte[] b = new byte[4];
        b[3] = (byte) (val & 0xff);
        b[2] = (byte) ((val >> 8) & 0xff);
        b[1] = (byte) ((val >> 16) & 0xff);
        b[0] = (byte) ((val >> 24) & 0xff);
        return b;
    }


    public static String bytesToHexString(byte[] src, int length) {
        StringBuilder stringBuilder = new StringBuilder("");
        if (src == null || length <= 0) {
            return null;
        }
        for (int i = 0; i < length; i++) {
            int v = src[i] & 0xFF;
            String hv = Integer.toHexString(v);
            hv = hv.toUpperCase();
            if (hv.length() < 2) {
                stringBuilder.append(0);
            }
            stringBuilder.append(hv);
        }
        return stringBuilder.toString();
    }

    /**
     * @param payload
     * @param ssrc
     * @param sampleRate 采样率
     */
    public void init(int payload, int ssrc, int sampleRate) {
        this.payload = payload;
        this.ssrc = ssrc;
        this.sampleRate = sampleRate;
        fillInHeader(0, 0);
    }

    private int getTimestamp() {
        currentTimestamp = duration * currentSeqNo;
        return currentTimestamp;
    }

    /**
     * 初始化默认的RTP结构
     *
     * @param timestamp
     */
    private void fillInHeader(int seqNo, int timestamp) {
        int version = 2;
        int padding = 0;
        int extension = 0;
        int csrc_len = 0;

        int marker = 1;//音频表示会话的开始
        // int payload = 8;

        //short seqNo = 0;

        //第一个字节
        int firstInt = version << 30;
        firstInt = firstInt | (padding << 29);
        firstInt = firstInt | (extension << 28);
        firstInt = firstInt | (csrc_len << 24);

        //第二个字节
        firstInt = firstInt | (marker << 23);
        firstInt = firstInt | (payload << 16);

        //第三 第四个字节
        firstInt = firstInt | seqNo;

        // ssrc 可以为随机数
        int ssrc = 13001;

        byte[] temp = intToBytes(firstInt);
        for (int i = 0; i < 4; i++) {
            rtpHeader[i] = temp[i];
        }

        temp = intToBytes(timestamp);
        for (int i = 0; i < 4; i++) {
            rtpHeader[i + 4] = temp[i];
        }

        temp = intToBytes(ssrc);
        for (int i = 0; i < 4; i++) {
            rtpHeader[i + 8] = temp[i];
        }
    }

    public byte[] sendG711A(byte[] data) {
        byte [] preHeader = new byte[2];
        preHeader[0] = ByteUtil.intToByte(2);
        preHeader[1] = ByteUtil.intToByte(182);
        fillInHeader(currentSeqNo, getTimestamp());
        System.arraycopy(preHeader, 0, g711A, 0, preHeader.length);
        System.arraycopy(data, 0, g711A, rtpHeader.length, data.length);
        // 增长序列
        currentSeqNo++;
        System.out.println("发送audio的前18个字节为:" + (rtpHeader.length + data.length) +
                " " + bytesToHexString(g711A, rtpHeader.length + data.length));
        return  g711A;
    }

}