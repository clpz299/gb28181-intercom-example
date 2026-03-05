package com.blue.butterball.gb28181.netty;


public class RtpPack {

    private byte[] rtpHeader = new byte[12];
    private int currentSeqNo = 0;
    private byte[] sendBuffer = new byte[1500]; // 默认足够大，不够时自动扩容
    private int payload = 8; // G.711 PCMA
    private int ssrc = 0;
    private int sampleRate = 8000;
    private long currentTimestamp = 0;
    private int duration = 8000 / 25; // 每帧默认时长，根据实际数据动态调整更佳

    /**
     * @param payload    Payload type
     * @param ssrc       SSRC identifier
     * @param sampleRate 采样率
     */
    public void init(int payload, int ssrc, int sampleRate) {
        this.payload = payload;
        this.ssrc = ssrc;
        this.sampleRate = sampleRate;
        // 重置序列号和时间戳
        this.currentSeqNo = 0;
        this.currentTimestamp = 0;
    }

    private int getTimestamp(int dataLen) {
        // G.711A 是 8000Hz, 8bit/sample -> 1 byte/sample
        // timestamp 增量 = 采样点数 = 字节数
        int increment = dataLen;
        int ts = (int) currentTimestamp;
        currentTimestamp += increment;
        return ts;
    }

    /**
     * 构建 RTP 头
     */
    private void fillInHeader(int seqNo, int timestamp, int marker) {
        int version = 2;
        int padding = 0;
        int extension = 0;
        int csrc_len = 0;

        // 第一个字节: V(2) P(1) X(1) CC(4)
        int firstByte = (version << 6) | (padding << 5) | (extension << 4) | csrc_len;

        // 第二个字节: M(1) PT(7)
        int secondByte = (marker << 7) | (payload & 0x7F);

        // 填充 RTP 头
        rtpHeader[0] = (byte) firstByte;
        rtpHeader[1] = (byte) secondByte;

        // 序列号 (16 bits)
        rtpHeader[2] = (byte) ((seqNo >> 8) & 0xFF);
        rtpHeader[3] = (byte) (seqNo & 0xFF);

        // 时间戳 (32 bits)
        rtpHeader[4] = (byte) ((timestamp >> 24) & 0xFF);
        rtpHeader[5] = (byte) ((timestamp >> 16) & 0xFF);
        rtpHeader[6] = (byte) ((timestamp >> 8) & 0xFF);
        rtpHeader[7] = (byte) (timestamp & 0xFF);

        // SSRC (32 bits)
        rtpHeader[8] = (byte) ((ssrc >> 24) & 0xFF);
        rtpHeader[9] = (byte) ((ssrc >> 16) & 0xFF);
        rtpHeader[10] = (byte) ((ssrc >> 8) & 0xFF);
        rtpHeader[11] = (byte) (ssrc & 0xFF);
    }

    public byte[] sendG711A(byte[] data) {
        if (data == null || data.length == 0) {
            return new byte[0];
        }

        // 计算总长度：2字节长度头 + 12字节RTP头 + 数据长度
        int totalLen = 2 + 12 + data.length;

        // 检查缓冲区是否足够
        if (sendBuffer.length < totalLen) {
            sendBuffer = new byte[totalLen + 1024]; // 扩容
        }

        // 填充 RTP 头
        // 第一个包或者关键帧设置 marker=1，这里音频流持续发送，通常第一个包设1，或者静音后第一个包设1
        // 简单起见，这里设为0，除非是第一个包
        int marker = (currentSeqNo == 0) ? 1 : 0;
        fillInHeader(currentSeqNo & 0xFFFF, getTimestamp(data.length), marker);

        // 1. 写入 TCP 长度头 (2字节, Big Endian)
        // 注意：这是 RTP over TCP 的封装格式 (RFC 4571)
        int rtpPacketLen = 12 + data.length;
        sendBuffer[0] = (byte) ((rtpPacketLen >> 8) & 0xFF);
        sendBuffer[1] = (byte) (rtpPacketLen & 0xFF);

        // 2. 写入 RTP 头 (12字节)
        System.arraycopy(rtpHeader, 0, sendBuffer, 2, 12);

        // 3. 写入 Payload 数据
        System.arraycopy(data, 0, sendBuffer, 14, data.length);

        // 序列号递增
        currentSeqNo++;

        // 返回实际有效的数据部分（创建一个新数组返回，或者修改接口返回 ByteBuffer/长度）
        // 原接口返回 byte[]，为了兼容性，这里只能拷贝一份
        byte[] result = new byte[totalLen];
        System.arraycopy(sendBuffer, 0, result, 0, totalLen);

        return result;
    }

    public static void main(String[] args) {
        RtpPack pack = new RtpPack();
        pack.init(8, 12345, 8000);

        byte[] data = new byte[]{0x01, 0x02, 0x03, 0x04};
        byte[] rtpPacket = pack.sendG711A(data);

        System.out.println("Payload length: " + data.length);
        System.out.println("RTP Packet total length: " + rtpPacket.length);
        // 2 (Length) + 12 (Header) + 4 (Payload) = 18
        System.out.println("Expected length: " + (2 + 12 + data.length));

        // 验证长度头 (Big Endian)
        int len = ((rtpPacket[0] & 0xFF) << 8) | (rtpPacket[1] & 0xFF);
        System.out.println("TCP Length Header: " + len);
        System.out.println("Expected Length Header: " + (12 + data.length));

        // 验证 RTP Version (10xxxxxx -> 0x80)
        System.out.println("RTP Header Byte 0: 0x" + Integer.toHexString(rtpPacket[2] & 0xFF));

        // 验证 Payload Type (8)
        System.out.println("RTP Header Byte 1 (PT=8): 0x" + Integer.toHexString(rtpPacket[3] & 0xFF));

        // 验证 Sequence Number (初始0)
        int seq = ((rtpPacket[4] & 0xFF) << 8) | (rtpPacket[5] & 0xFF);
        System.out.println("Sequence Number: " + seq);

        // 发送第二个包
        byte[] rtpPacket2 = pack.sendG711A(data);
        int seq2 = ((rtpPacket2[4] & 0xFF) << 8) | (rtpPacket2[5] & 0xFF);
        System.out.println("2nd Packet Sequence Number: " + seq2);

        // 验证 Timestamp (第一个包 data.length = 4, timestamp 应该增加 4)
        long ts1 = ((rtpPacket[6] & 0xFFL) << 24) | ((rtpPacket[7] & 0xFFL) << 16) | ((rtpPacket[8] & 0xFFL) << 8) | (rtpPacket[9] & 0xFFL);
        long ts2 = ((rtpPacket2[6] & 0xFFL) << 24) | ((rtpPacket2[7] & 0xFFL) << 16) | ((rtpPacket2[8] & 0xFFL) << 8) | (rtpPacket2[9] & 0xFFL);
        System.out.println("Timestamp 1: " + ts1);
        System.out.println("Timestamp 2: " + ts2);
        System.out.println("Timestamp Delta: " + (ts2 - ts1));
    }
}

