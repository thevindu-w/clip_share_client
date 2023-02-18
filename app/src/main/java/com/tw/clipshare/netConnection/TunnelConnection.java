package com.tw.clipshare.netConnection;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;

public class TunnelConnection extends ServerConnection {
    public TunnelConnection(String address) throws IOException {
        super();
        Socket tunnel = TunnelManager.getConnection(address);
        if (tunnel == null) {
            throw new SocketException("No tunnel available for " + address);
        }
        this.socket = tunnel;
        this.inStream = this.socket.getInputStream();
        this.outStream = this.socket.getOutputStream();
    }
}
