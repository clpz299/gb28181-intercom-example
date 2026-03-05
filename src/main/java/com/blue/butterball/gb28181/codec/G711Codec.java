package com.blue.butterball.gb28181.codec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * G.711 A-law 编解码器
 * <p>
 * 实现了 PCM (16-bit) 与 G.711 A-law (8-bit) 之间的相互转换。
 * G.711 是国际电信联盟 (ITU-T) 制定的音频编码标准，主要用于电话语音传输。
 * </p>
 */
public class G711Codec extends AudioCodec {
    private static final int SIGN_BIT = 0x80;
    private static final int QUANT_MASK = 0xf;
    private static final int SEG_SHIFT = 4;
    private static final int SEG_MASK = 0x70;

    /**
     * A-law 压缩所需的段界限表
     */
    private static final short[] SEG_END = {0xFF, 0x1FF, 0x3FF, 0x7FF, 0xFFF, 0x1FFF, 0x3FFF, 0x7FFF};

    /**
     * 查找值在表中的位置
     *
     * @param val   要查找的值
     * @param table 查找表
     * @param size  表的大小
     * @return 值所在的段索引
     */
    static short search(short val, short[] table, short size) {
        for (short i = 0; i < size; i++) {
            if (val <= table[i]) {
                return i;
            }
        }
        return size;
    }

    /**
     * 将 16-bit 线性 PCM 值转换为 8-bit A-law 值
     *
     * @param pcmVal 输入的 16-bit PCM 值
     * @return 转换后的 8-bit A-law 值
     */
    public static byte linear2alaw(short pcmVal) {
        short mask;
        short seg;
        char aval;

        // A-law 算法：符号位处理
        if (pcmVal >= 0) {
            mask = 0xD5; // 0xD5 = 1101 0101 (A-law 规定偶数位取反)
        } else {
            mask = 0x55; // 0x55 = 0101 0101 (负数符号位为1，取反后为0，但A-law符号位也要取反)
            // 将负数转为正数处理，注意 -32768 的边界情况
            pcmVal = (short) (-pcmVal - 1);
            if (pcmVal < 0) {
                pcmVal = 32767;
            }
        }

        /* Convert the scaled magnitude to segment number. */
        // 查找所在的段
        seg = search(pcmVal, SEG_END, (short) 8);

        /* Combine the sign, segment, and quantization bits. */
        // 组合符号、段号和量化位

        if (seg >= 8) {
            /* out of range, return maximum value. */
            return (byte) (0x7F ^ mask);
        } else {
            aval = (char) (seg << SEG_SHIFT);
            if (seg < 2) {
                aval |= (pcmVal >> 4) & QUANT_MASK;
            } else {
                aval |= (pcmVal >> (seg + 3)) & QUANT_MASK;
            }
            return (byte) (aval ^ mask);
        }
    }

    /**
     * 将 8-bit A-law 值转换为 16-bit 线性 PCM 值
     *
     * @param aVal 输入的 8-bit A-law 值
     * @return 转换后的 16-bit PCM 值
     */
    public static short alaw2linear(byte aVal) {
        short t;
        short seg;

        // A-law 解码：异或 0x55 还原原始位（A-law 编码时偶数位被取反）
        aVal ^= 0x55;

        // 提取量化位（低4位）并左移4位
        t = (short) ((aVal & QUANT_MASK) << 4);
        // 提取段号（中间3位）
        seg = (short) ((aVal & SEG_MASK) >> SEG_SHIFT);
        
        switch (seg) {
            case 0:
                t += 8;
                break;
            case 1:
                t += 0x108;
                break;
            default:
                t += 0x108;
                t <<= seg - 1;
        }
        // 符号位处理：如果最高位为1（因为异或过0x55，原始符号位为0表示正数，这里最高位不为0表示正数）
        // 等等，G.711 A-law 符号位：1为正，0为负（编码后）。
        // 原始 PCM：0为正。
        // linear2alaw: pcm >= 0 -> mask = 0xD5 (11010101). pcm < 0 -> mask = 0x55 (01010101).
        // 最终 return (aval ^ mask).
        // 解码时：aVal ^= 0x55.
        // 如果原始是正数 (mask=0xD5)，则 0xD5 ^ 0x55 = 0x80 (10000000)，符号位为1。
        // 如果原始是负数 (mask=0x55)，则 0x55 ^ 0x55 = 0x00 (00000000)，符号位为0。
        // 所以 (aVal & SIGN_BIT) != 0 表示原始是正数。
        return (aVal & SIGN_BIT) != 0 ? t : (short) -t;
    }

    /**
     * G.711 A-law 转 PCM
     *
     * @param g711data G.711 A-law 字节数组
     * @return PCM 字节数组 (Little Endian)
     */
    public static byte[] decodeToPCM(byte[] g711data) {
        if (g711data == null) {
            return null;
        }
        byte[] pcmdata = new byte[g711data.length * 2];
        for (int i = 0, k = 0; i < g711data.length; i++) {
            short v = alaw2linear(g711data[i]);
            // Little Endian: 低字节在前
            pcmdata[k++] = (byte) (v & 0xff);
            pcmdata[k++] = (byte) ((v >> 8) & 0xff);
        }
        return pcmdata;
    }

    /**
     * PCM 转 G.711 A-law
     *
     * @param pcmdata PCM 字节数组 (Little Endian)
     * @return G.711 A-law 字节数组
     */
    public static byte[] encodeToG711A(byte[] pcmdata) {
        if (pcmdata == null) {
            return null;
        }
        byte[] g711data = new byte[pcmdata.length / 2];
        for (int i = 0, k = 0; i < pcmdata.length; i += 2, k++) {
            // Little Endian: 低字节在前，高字节在后
            short v = (short) (((pcmdata[i + 1] & 0xff) << 8) | (pcmdata[i] & 0xff));
            g711data[k] = linear2alaw(v);
        }
        return g711data;
    }

    /**
     * 将 G.711 数据转换为 PCM 数据
     * <p>
     * 包含对海思头 (Hisilicon Header) 的检测与移除逻辑。
     * 海思头特征：前4字节为 00 01 len 00，其中 len 为剩余数据长度的一半。
     * </p>
     *
     * @param data 输入的音频数据
     * @return 转换后的 PCM 数据
     */
    @Override
    public byte[] toPCM(byte[] data) {
        if (data == null || data.length == 0) {
            return new byte[0];
        }

        byte[] temp;
        // 如果前四字节是00 01 52 00，则是海思头，需要去掉
        // 校验逻辑：data[0]==0x00, data[1]==0x01, data[3]==0x00, data[2] == (length-4)/2
        // 注意：data[2] 是 byte，最大 127，如果包很大，这个检查可能会有问题，但这是针对特定硬件的协议
        if (data.length >= 4 && 
            data[0] == 0x00 && 
            data[1] == 0x01 && 
            (data[2] & 0xff) == (data.length - 4) / 2 && 
            data[3] == 0x00) {
            
            temp = new byte[data.length - 4];
            System.arraycopy(data, 4, temp, 0, temp.length);
        } else {
            temp = data;
        }

        return decodeToPCM(temp);
    }

    @Override
    public byte[] fromPCM(byte[] data) {
        return encodeToG711A(data);
    }

    // 为了兼容旧代码，保留原名方法，标记为 Deprecated 或直接调用新方法
    public static byte[] _toPCM(byte[] g711data) {
        return decodeToPCM(g711data);
    }

    public static byte[] _fromPCM(byte[] pcmdata) {
        return encodeToG711A(pcmdata);
    }

    public static void main(String[] args) {
        // 使用 try-with-resources 自动关闭流
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(1024 * 1024 * 4);
             FileInputStream fis = new FileInputStream("input.pcm");
             FileOutputStream fos = new FileOutputStream("output.g711")) {

            int len;
            byte[] block = new byte[512];
            
            while ((len = fis.read(block)) > -1) {
                baos.write(block, 0, len);
            }

            byte[] pcmData = baos.toByteArray();
            if (pcmData.length > 0) {
                byte[] g711Data = encodeToG711A(pcmData);
                fos.write(g711Data);
                System.out.println("Conversion complete. Input PCM size: " + pcmData.length + ", Output G.711 size: " + g711Data.length);
            } else {
                System.out.println("Input file is empty.");
            }

        } catch (IOException e) {
            System.err.println("Error during conversion: " + e.getMessage());
            // 在实际项目中，不要吞掉异常，或者记录日志
            e.printStackTrace();
        }
    }
}