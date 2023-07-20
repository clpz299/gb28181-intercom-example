package com.blue.butterball.gb28181.netty;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class BroadcastServer  extends Thread {
    private final int MAX_CONNECTIONS = 2;
    private final int PORT;
    private ServerSocket server;
    private volatile ArrayList<Connection> connectedClients;
    private ListenThread listen;

    private int fileIndex;

    public BroadcastServer() {
        this.PORT = BroadcastGlobals._PORT++;
        connectedClients = new ArrayList<>();
    }

    public int getPort() {
        return this.PORT;
    }

    @Override
    public void run() {
        try {
            server = new ServerSocket(this.PORT);
            BroadcastGlobals.ADD_SERVER(this);
            listen = new ListenThread(server);
            listen.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    class Connection extends Thread {
        private Socket socket;
        private OutputStream out;
        public Connection(Socket socket, InputStream in, OutputStream out) {
            this.socket = socket;
            this.out = out;
            fileIndex = 0;
        }

        // 读取录音转码发送
        @Override
        public void run() {
            RtpPack rp = new RtpPack();

            String recorderDir = "D:\\recorders\\" + DateUtil.today() + "\\" + PORT;
            try {
                this.out.flush();
                while (true) {
                    List<File> recorders = FileUtil.loopFiles(recorderDir);
                    if (fileIndex >= recorders.size()) continue;
                    if (recorders.size() < 1) continue;
                    byte[] bytes = FileUtils.readFileToByteArray(recorders.get(fileIndex));
                    fileIndex++;
                    rp.init(8,12,8000);
                    out.write(rp.sendG711A(bytes));
                    out.flush();
                }

            } catch (Exception e) {
                e.printStackTrace();
                close();
            }

        }
    }


    class ListenThread extends Thread {
        private ServerSocket server;
        public ListenThread(ServerSocket server) {
            this.server = server;
        }
        @Override
        public void run() {
            while (true) {
                while (connectedClients.size() < MAX_CONNECTIONS && !server.isClosed()) {
                    Socket client;
                    try {
                        client = server.accept();
                        System.out.println("Accepted Connection from " + client.toString());
                        Connection conn = new Connection(client, client.getInputStream(), client.getOutputStream());
                        connectedClients.add(conn);
                        conn.start();
                    } catch (IOException e) {

                        e.printStackTrace();
                    }
                }
                if (server.isClosed()) break;
            }
        }
    }

    public void close() {
        try {
            fileIndex = 0;
            // 会话关闭之后删除录音文件
            FileUtil.del("D:\\recorders\\" + DateUtil.today()
                    + "\\" + PORT);
            server.close();
            System.out.println("Server closed.");
        } catch (IOException e) {
            System.out.println("close error");
        }

    }
}
