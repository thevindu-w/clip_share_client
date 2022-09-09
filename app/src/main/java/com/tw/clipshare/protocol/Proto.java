package com.tw.clipshare.protocol;

import com.tw.clipshare.netConnection.ServerConnection;
import com.tw.clipshare.platformUtils.AndroidUtils;
import com.tw.clipshare.platformUtils.StatusNotifier;

public abstract class Proto {
    protected static final byte GET_TEXT = 1;
    protected static final byte SEND_TEXT = 2;
    protected static final byte GET_FILE = 3;
    protected static final byte SEND_FILE = 4;
    protected static final byte GET_IMAGE = 5;
    protected static final byte INFO = 125;

    protected static final byte STATUS_OK = 1;
    protected static final int BUF_SZ = 65536;

    protected final ServerConnection serverConnection;
    protected final AndroidUtils utils;
    protected final StatusNotifier notifier;

    protected Proto(ServerConnection serverConnection, AndroidUtils utils, StatusNotifier notifier) {
        this.serverConnection = serverConnection;
        this.utils = utils;
        this.notifier = notifier;
    }

    protected long readSize() {
        byte[] data = new byte[8];
        if (this.serverConnection.receive(data)) {
            return -1;
        }
        long size = 0;
        for (byte b : data) {
            size = (size << 8) | (b & 0xFF);
        }
        return size;
    }

    protected boolean sendSize(long size) {
        byte[] data = new byte[8];
        for (int i = data.length - 1; i >= 0; i--) {
            data[i] = (byte) (size & 0xFF);
            size >>= 8;
        }
        return !this.serverConnection.send(data);
    }

    protected boolean methodInit(byte method) {
        byte[] methodArr = {method};
        if (!this.serverConnection.send(methodArr)) {
            return true;
        }
        byte[] status = new byte[1];
        return (this.serverConnection.receive(status) || status[0] != STATUS_OK);
    }

    protected byte[] readData() {
        long size = this.readSize();
        if (size < 0 || size >= 16777216) {
            return null;
        }
        byte[] data = new byte[(int) size];
        if (this.serverConnection.receive(data)) {
            return null;
        }
        return data;
    }

    /**
     * Sends a byte array to server
     *
     * @param data data to be sent
     * @return true on success or false on error
     */
    protected boolean sendData(byte[] data) {
        int len = data.length;
        if (len <= 0 || len >= 16777216) {
            return false;
        }
        if (this.sendSize(len)) {
            return false;
        }
        return this.serverConnection.send(data);
    }

    public abstract String getText();

    public abstract boolean sendText(String text);

    public abstract boolean getFile();

    public abstract boolean sendFile();

    public abstract boolean getImage();

    public abstract String checkInfo();
}
