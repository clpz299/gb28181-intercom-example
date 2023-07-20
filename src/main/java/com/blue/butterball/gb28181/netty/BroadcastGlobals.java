package com.blue.butterball.gb28181.netty;

import java.util.LinkedList;

public class BroadcastGlobals {
    public static int _PORT = 7;
    public static String _IP = "127.0.0.1";

    private static LinkedList<BroadcastServer> _ACTIVE_SERVERS = new LinkedList<>();

    public static synchronized void ADD_SERVER(BroadcastServer server) {
        _ACTIVE_SERVERS.add(server);
    }

    public static synchronized LinkedList<BroadcastServer> GET_SERVERS() {
        return _ACTIVE_SERVERS;
    }
}
