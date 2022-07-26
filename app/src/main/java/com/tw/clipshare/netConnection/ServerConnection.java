package com.tw.clipshare.netConnection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public abstract class ServerConnection {

    protected OutputStream outStream;
    protected InputStream inStream;
    protected Socket socket;

    protected ServerConnection() {
        this.socket = null;
    }

    protected ServerConnection(Socket socket) {
        this.socket = socket;
    }

    public boolean send(byte[] data, int offset, int length) {
        try {
            outStream.write(data, offset, length);
            return true;
        } catch (RuntimeException | IOException ex) {
            return false;
        }
    }

    public boolean receive(byte[] buffer, int offset, int length) {
        int remaining = length;
        try {
            while (remaining > 0) {
                int read = inStream.read(buffer, offset, remaining);
                if (read > 0) {
                    offset += read;
                    remaining -= read;
                } else if (read < 0) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException | IOException ex) {
            return true;
        }
    }

    public boolean send(byte[] data) {
        return this.send(data, 0, data.length);
    }

    public boolean receive(byte[] buffer) {
        return this.receive(buffer, 0, buffer.length);
    }

    public void close() {
        try {
            this.socket.close();
        } catch (RuntimeException | IOException ignored) {
        }
    }

}
