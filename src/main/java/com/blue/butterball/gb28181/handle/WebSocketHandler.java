package com.blue.butterball.gb28181.handle;

import cn.hutool.core.codec.Base64Decoder;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpRequest;
import com.alibaba.fastjson.JSON;
import com.blue.butterball.gb28181.codec.G711Codec;
import com.blue.butterball.gb28181.netty.BroadcastServer;
import com.blue.butterball.gb28181.netty.NettyChannelHandlerPool;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.AttributeKey;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WebSocketHandler extends SimpleChannelInboundHandler<Object> {
    private final String USER = "deviceId";

    private final AttributeKey<String> key = AttributeKey.valueOf(USER);

    BroadcastServer server;


    // 客户端链接
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        System.out.println("与客户端建立连接，通道开启！");
        System.out.println(ctx);

        //添加到channelGroup通道组
        NettyChannelHandlerPool.channelGroup.add(ctx.channel());
    }

    // 客户端关闭
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        System.out.println("与客户端断开连接，通道关闭！");
        if (server != null) {
            server.close();
            NettyChannelHandlerPool.channelGroup.remove(ctx.channel());
        }
    }

    // 消息接收
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // 首次连接是FullHttpRequest
        if (null != msg && msg instanceof FullHttpRequest) {
            handleHttpRequest(ctx, (FullHttpRequest) msg);
        } else if (msg instanceof WebSocketFrame) {
            handleWebSocketFrame(ctx, (WebSocketFrame) msg);
        }
        super.channelRead(ctx, msg);
    }
    

    private String getRecorderDir(int port) {
        // 使用项目根目录下的 recorders 文件夹
        String projectDir = System.getProperty("user.dir");
        return projectDir + File.separator + "recorders" + File.separator + DateUtil.today() + File.separator + port + File.separator;
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest request) {
        server = new BroadcastServer();
        server.start();
        
        // 删除上一次的录音文件
        String dirStr = getRecorderDir(server.getPort());
        FileUtil.del(dirStr);
        
        String uri = request.uri();
        Map<String, String> paramMap = getUrlParams(uri);
        System.out.println("接收到的参数是：" + JSON.toJSONString(paramMap));
        
        String deviceId = paramMap.get("deviceId");
        if (deviceId != null) {
             online(deviceId, ctx.channel());
             // 发送广播调用（wvp-gb28181接口）
             // 注意：这里使用 HTTP 请求是阻塞的，生产环境建议异步
             try {
                HttpRequest.get("https://127.0.0.1:8843/api/play/broadcast/" + deviceId).execute(true);
             } catch (Exception e) {
                 System.err.println("调用 WVP 接口失败: " + e.getMessage());
             }
        }
        
        //如果url包含参数，需要处理，以便 WebSocket 握手成功
        if (uri.contains("?")) {
            String newUri = uri.substring(0, uri.indexOf("?"));
            request.setUri(newUri);
        }
    }

    private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) throws IOException {
        byte[] pcmData = null;

        if (frame instanceof TextWebSocketFrame) {
            // Base64 文本模式
            String text = ((TextWebSocketFrame) frame).text();
            pcmData = Base64Decoder.decode(text);
        } else if (frame instanceof BinaryWebSocketFrame) {
            // 二进制模式 (性能更优)
            ByteBuf content = frame.content();
            pcmData = new byte[content.readableBytes()];
            content.readBytes(pcmData);
        }

        if (pcmData != null && pcmData.length > 0) {
            processPcmData(ctx, pcmData);
        }
    }

    private void processPcmData(ChannelHandlerContext ctx, byte[] pcmData) throws IOException {
        // 转码 PCM -> G.711A
        byte[] g711a = G711Codec.encodeToG711A(pcmData);
        
        String dirStr = getRecorderDir(server.getPort());

        FileUtil.mkdir(dirStr);
        // 使用时间戳作为文件名，注意：高并发下 System.currentTimeMillis() 可能重复，建议加随机数或原子计数器
        // 但这里是单连接单线程处理（Netty EventLoop），只要处理速度够快通常没问题，或者用 System.nanoTime()
        String fileName = System.currentTimeMillis() + ".pcm"; 
        try (FileOutputStream out = new FileOutputStream(dirStr + fileName, false)) {
            out.write(g711a);
        }
        
        // 回显逻辑 (可选)
        /*
        String deviceId = ctx.channel().attr(key).get();
        if (deviceId != null) {
            List<Channel> channelList = getChannelByName(deviceId);
            // 注意：回显通常不需要发回给自己，或者是为了调试
            // 如果要发回二进制，需要用 BinaryWebSocketFrame
            // channelList.forEach(channel -> channel.writeAndFlush(new BinaryWebSocketFrame(Unpooled.copiedBuffer(pcmData))));
        }
        */
    }

    /**
     * 根据用户id查找channel
     *
     * @param name
     * @return
     */
    public List<Channel> getChannelByName(String name) {
        return NettyChannelHandlerPool.channelGroup.stream().filter(channel -> channel.attr(key).get().equals(name))
                .collect(Collectors.toList());
    }

    /**
     * 上线一个用户
     *
     * @param channel
     * @param userId
     */
    private void online(String userId, Channel channel) {
        // 保存channel通道的附带信息，以用户的deviceId为标识
        channel.attr(key).setIfAbsent(userId);
        NettyChannelHandlerPool.channelGroup.add(channel);
    }


    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, Object msg) throws Exception {
        // Netty 5.x 才有 channelRead0 in SimpleChannelInboundHandler<Object> ? 
        // 4.x SimpleChannelInboundHandler<T> 的 channelRead0 是抽象方法
        // 这里如果是 Object 泛型，需要实现。但上面 override 了 channelRead，通常 channelRead0 是给指定类型用的。
        // 为了兼容性，将类定义改为 SimpleChannelInboundHandler<Object> 并在此处分发
        if (msg instanceof FullHttpRequest) {
            handleHttpRequest(channelHandlerContext, (FullHttpRequest) msg);
        } else if (msg instanceof WebSocketFrame) {
             handleWebSocketFrame(channelHandlerContext, (WebSocketFrame) msg);
        }
    }

    private static Map getUrlParams(String url) {
        Map<String, String> map = new HashMap<>();
        url = url.replace("?", ";");
        if (!url.contains(";")) {
            return map;
        }
        if (url.split(";").length > 0) {
            String[] arr = url.split(";")[1].split("&");
            for (String s : arr) {
                String key = s.split("=")[0];
                String value = s.split("=")[1];
                map.put(key, value);
            }
            return map;

        } else {
            return map;
        }
    }
}
