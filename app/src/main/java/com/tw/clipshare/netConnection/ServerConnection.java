package com.tw.clipshare.netConnection;

public interface ServerConnection {
    boolean send(byte[] data);

    boolean send(byte[] data, int offset, int length);

    boolean receive(byte[] buffer);

    boolean receive(byte[] buffer, int offset, int length);

    void close();
}
