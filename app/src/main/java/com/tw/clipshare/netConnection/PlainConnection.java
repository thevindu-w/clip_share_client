package com.tw.clipshare.netConnection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

public class PlainConnection implements ServerConnection {

    private static final int PORT = 4337;
    private final OutputStream outStream;
    private final InputStream inStream;
    private final Socket socket;

    public PlainConnection(InetAddress serverAddress) throws IOException {
        this.socket = new Socket();
        this.socket.connect(new InetSocketAddress(serverAddress, PORT), 500);
        this.outStream = this.socket.getOutputStream();
        this.inStream = this.socket.getInputStream();
    }

    @Override
    public boolean send(byte[] data) {
        return this.send(data, 0, data.length);
    }

    @Override
    public boolean receive(byte[] buffer) {
        return this.receive(buffer, 0, buffer.length);
    }

    @Override
    public void close() {
        try {
            this.socket.close();
        } catch (RuntimeException | IOException ignored) {
        }
    }

    @Override
    public boolean send(byte[] data, int offset, int length) {
        try {
            outStream.write(data, offset, length);
            return true;
        } catch (RuntimeException | IOException ex) {
            return false;
        }
    }

    @Override
    public boolean receive(byte[] buffer, int offset, int length) {
        int remaining = length;
        try {
            while (remaining > 0) {
                int read = inStream.read(buffer, offset, remaining);
                if (read > 0) {
                    offset += read;
                    remaining -= read;
                } else if (read < 0) {
                    return false;
                }
            }
            return true;
        } catch (RuntimeException | IOException ex) {
            return false;
        }
    }
}
