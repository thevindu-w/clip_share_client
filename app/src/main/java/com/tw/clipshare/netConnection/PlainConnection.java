package com.tw.clipshare.netConnection;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

public class PlainConnection extends ServerConnection {

    private static final int PORT = 4337;

    public PlainConnection(InetAddress serverAddr) throws IOException {
        super(new Socket());
        this.socket.connect(new InetSocketAddress(serverAddr, PORT), 500);
        this.inStream = this.socket.getInputStream();
        this.outStream = this.socket.getOutputStream();
    }
}
