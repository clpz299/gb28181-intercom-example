package com.blue.butterball.gb28181.netty;

import com.blue.butterball.gb28181.handle.WebSocketHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.bytes.ByteArrayDecoder;
import io.netty.handler.codec.bytes.ByteArrayEncoder;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;

public class NettyServer {
    private final int port;

    public NettyServer(int port) {
        this.port = port;
    }

    public void start() throws Exception {
        //配置服务端的NIO线程组
        EventLoopGroup bossGroup = new NioEventLoopGroup();

        EventLoopGroup group = new NioEventLoopGroup();
        try {
            ServerBootstrap sb = new ServerBootstrap();
            //服务端接受连接的队列长度，如果队列已满，客户端连接将被拒绝
            sb.option(ChannelOption.SO_BACKLOG, 512);
            // 绑定线程池
            sb.group(group, bossGroup)
                    // 指定使用的channel
                    .channel(NioServerSocketChannel.class)
                    .localAddress(this.port)// 绑定监听端口
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        // 绑定客户端连接时候触发操作
                        //服务端初始化，客户端与服务器端连接一旦创建，这个类中方法就会被回调，设置出站编码器和入站解码器
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(new HttpServerCodec());
                            //以块的方式来写的处理器
                            ch.pipeline().addLast(new ChunkedWriteHandler());
                            ch.pipeline().addLast(new HttpObjectAggregator(7212));
                            ch.pipeline().addLast(new ByteArrayDecoder());
                            ch.pipeline().addLast(new ByteArrayEncoder());
                            ch.pipeline().addLast(new WebSocketHandler());
                            // 指定前端 webservice 链接的路径
                            ch.pipeline().addLast(new WebSocketServerProtocolHandler("/ws_pcm", null, true, 65536 * 10));
                        }
                    });
            // 异步绑定
            ChannelFuture cf = sb.bind().sync();
            cf.channel().closeFuture().sync();
        } finally {
            // 释放线程池资源
            group.shutdownGracefully().sync();
            bossGroup.shutdownGracefully().sync();
        }
    }

}
