/*
 * MIT License
 *
 * Copyright (c) 2022-2023 H. Thevindu J. Wijesekera
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tw.clipshare.protocol;

import com.tw.clipshare.netConnection.ServerConnection;
import com.tw.clipshare.platformUtils.AndroidUtils;
import com.tw.clipshare.platformUtils.StatusNotifier;

import java.nio.charset.StandardCharsets;

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

    /**
     * Reads a String encoded with UTF-8 from server
     *
     * @param maxSize maximum size to read
     * @return read string or null on error
     */
    protected String readString(int maxSize) {
        long size = this.readSize();
        if (size < 0 || size > maxSize) {
            return null;
        }
        byte[] data = new byte[(int) size];
        if (this.serverConnection.receive(data)) {
            return null;
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    /**
     * Sends a String encoded with UTF-8 to server
     *
     * @param data String to be sent
     * @return true on success or false on error
     */
    protected boolean sendString(String data) {
        if (data == null) return false;
        final byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        final int len = bytes.length;
        if (len >= 16777216) {
            return false;
        }
        if (this.sendSize(len)) {
            return false;
        }
        return this.serverConnection.send(bytes);
    }

    /**
     * Close the connection used for communicating with the server
     */
    public void close() {
        try {
            if (this.serverConnection != null) this.serverConnection.close();
        } catch (Exception ignored) {
        }
    }

    public abstract String getText();

    public abstract boolean sendText(String text);

    public abstract boolean getFile();

    public abstract boolean sendFile();

    public abstract boolean getImage();

    public abstract String checkInfo();
}
