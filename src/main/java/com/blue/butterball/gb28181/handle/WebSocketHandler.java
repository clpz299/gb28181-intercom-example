package com.blue.butterball.gb28181.handle;

import cn.hutool.core.codec.Base64Decoder;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpRequest;
import com.alibaba.fastjson.JSON;
import com.blue.butterball.gb28181.codec.G711Codec;
import com.blue.butterball.gb28181.netty.BroadcastServer;
import com.blue.butterball.gb28181.netty.NettyChannelHandlerPool;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.AttributeKey;

import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WebSocketHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
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
            server = new BroadcastServer();
            server.start();
            // 删除上一次的录音文件
            String dirStr = "D:\\recorders\\" + DateUtil.today()
                    + "\\" + server.getPort()
                    + "\\";
            FileUtil.del(dirStr);
            FullHttpRequest request = (FullHttpRequest) msg;
            String uri = request.uri();
            Map<String, String> paramMap = getUrlParams(uri);
            System.out.println("接收到的参数是：" + JSON.toJSONString(paramMap));
            online(paramMap.get("deviceId"), ctx.channel());
            String deviceId = paramMap.get("deviceId");
            // 发送广播调用（wvp-gb28181接口）
            HttpRequest.get("https://127.0.0.1:8843/api/play/broadcast/" + deviceId).execute(true);
            System.out.println("接收到的参数是：" + JSON.toJSONString(paramMap));
            //如果url包含参数，需要处理
            if (uri.contains("?")) {
                String newUri = uri.substring(0, uri.indexOf("?"));
                System.out.println(newUri);
                request.setUri(newUri);
            }

        } else if (msg instanceof TextWebSocketFrame) {
            //正常的TEXT消息类型
            TextWebSocketFrame frame = (TextWebSocketFrame) msg;
            //以下是每次收到消息之后回给client的代码，如果不需要回复则可以注释了。
            String deviceId = ctx.channel().attr(key).get();
            List<Channel> channelList = getChannelByName(deviceId);
            if (channelList.size() <= 0) {
                System.out.println("用户" + deviceId + "不在线！");
            }
            // 前端传输 base64 的 pcm，此处要进行转码
            byte[] pcmData = Base64Decoder.decode(frame.text());
            byte[] g711a = G711Codec._fromPCM(pcmData);
            String dirStr = "D:\\recorders\\" + DateUtil.today()
                    + "\\" + server.getPort()
                    + "\\";

            FileUtil.mkdir(dirStr);
            FileOutputStream out = new FileOutputStream(dirStr + System.currentTimeMillis() + ".pcm", false);
            out.write(g711a);
            out.close();
            channelList.forEach(channel -> channel.writeAndFlush(new TextWebSocketFrame(frame.text())));
        }
        super.channelRead(ctx, msg);
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
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, TextWebSocketFrame textWebSocketFrame) throws Exception {

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
