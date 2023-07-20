package com.blue.butterball.gb28181;

import com.blue.butterball.gb28181.netty.BroadcastGlobals;
import com.blue.butterball.gb28181.netty.BroadcastServer;
import com.blue.butterball.gb28181.netty.NettyServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.LinkedList;

@SpringBootApplication
public class GB28181IntercomExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(GB28181IntercomExampleApplication.class, args);
        try {
            // 用来接收前端传来的 pcm
            new NettyServer(7211).start();

            // 处理广播消息
            Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    LinkedList<BroadcastServer> servers = BroadcastGlobals.GET_SERVERS();
                    for (int i = 0; i < servers.size(); i++)
                        servers.get(i).close();
                }
            });

        } catch (Exception e) {
            System.out.println("NettyServerError:" + e.getMessage());
        }
    }

}
