package com.tw.clipshare.protocol;

import com.tw.clipshare.netConnection.ServerConnection;
import com.tw.clipshare.platformUtils.AndroidUtils;
import com.tw.clipshare.platformUtils.FSUtils;
import com.tw.clipshare.platformUtils.StatusNotifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class Proto_v1 {

    private static final byte GET_TEXT = 1;
    private static final byte SEND_TEXT = 2;
    private static final byte GET_FILE = 3;
    private static final byte SEND_FILE = 4;
    private static final byte GET_IMAGE = 5;

    private static final byte STATUS_OK = 1;
    private static final int BUF_SZ = 65536;
    private final ServerConnection serverConnection;
    private final AndroidUtils utils;
    private final StatusNotifier notifier;

    Proto_v1(ServerConnection serverConnection, AndroidUtils utils, StatusNotifier notifier) {
        this.serverConnection = serverConnection;
        this.utils = utils;
        this.notifier = notifier;
    }

    private long readSize() {
        byte[] data = new byte[8];
        if (!this.serverConnection.receive(data)) {
            return -1;
        }
        long size = 0;
        for (byte b : data) {
            size = (size << 8) | (b & 0xFF);
        }
        return size;
    }

    private boolean sendSize(long size) {
        byte[] data = new byte[8];
        for (int i = data.length - 1; i >= 0; i--) {
            data[i] = (byte) (size & 0xFF);
            size >>= 8;
        }
        return this.serverConnection.send(data);
    }

    private boolean methodInit(byte method) {
        byte[] methodArr = {method};
        if (!this.serverConnection.send(methodArr)) {
            return false;
        }
        byte[] status = new byte[1];
        return (this.serverConnection.receive(status) && status[0] == STATUS_OK);
    }

    public String getText() {
        if (!methodInit(GET_TEXT)) {
            return null;
        }
        long len = readSize();
        if (len <= 0 || len > 16000000) {
            return null;
        }
        byte[] data = new byte[(int) len];
        if (!this.serverConnection.receive(data)) {
            return null;
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    public boolean sendText(String text) {
        if (text == null) {
            return false;
        }
        byte[] data;
        data = text.getBytes(StandardCharsets.UTF_8);
        int len = data.length;
        if (len <= 0 || len > 16000000) {
            return false;
        }
        if (!methodInit(SEND_TEXT)) {
            return false;
        }

        if (!sendSize(len)) {
            return false;
        }
        return this.serverConnection.send(data);
    }

    public boolean getFile() {
        if (!(this.utils instanceof FSUtils)) return false;
        FSUtils fsUtils = (FSUtils) this.utils;
        if (!methodInit(GET_FILE)) {
            return false;
        }
        long fileCnt = readSize();
        for (long fileNum = 0; fileNum < fileCnt; fileNum++) {
            int fileName_len = (int) readSize();
            if (fileName_len <= 0) {
                return false;
            }
            byte[] fileName_data = new byte[fileName_len];
            if (!this.serverConnection.receive(fileName_data)) {
                return false;
            }
            String fileName;
            fileName = new String(fileName_data, StandardCharsets.UTF_8);
            long file_size = readSize();
            if (file_size <= 0) {
                return false;
            }
            OutputStream out = fsUtils.getFileOutStream(fileName);
            if (out == null) {
                return false;
            }
            byte[] buf = new byte[BUF_SZ];
            int progressCurrent;
            long tot_sz = file_size;
            if (this.notifier != null) this.notifier.reset();
            if (this.notifier != null) this.notifier.setName("Getting file " + fileName);
            while (file_size > 0) {
                int read_sz = (int) Math.min(file_size, BUF_SZ);
                if (!this.serverConnection.receive(buf, 0, read_sz)) {
                    return false;
                }
                file_size -= read_sz;
                progressCurrent = (int) (((tot_sz - file_size) * 100) / tot_sz);
                if (this.notifier != null) this.notifier.setStatus(progressCurrent);
                try {
                    out.write(buf, 0, read_sz);
                } catch (IOException ex) {
                    return false;
                }
            }
            try {
                out.close();
                fsUtils.getFileDone("file");
            } catch (IOException ignored) {
            }
        }
        if (this.notifier != null) this.notifier.finish();
        return true;
    }

    public boolean sendFile() {
        if (!(this.utils instanceof FSUtils)) return false;
        FSUtils fsUtils = (FSUtils) this.utils;
        String fileName = fsUtils.getFileName();
        if (fileName == null) {
            return false;
        }
        byte[] name_data;
        name_data = fileName.getBytes(StandardCharsets.UTF_8);
        long fileSize = fsUtils.getFileSize();
        if (fileSize <= 0) {
            return false;
        }
        InputStream inStream = fsUtils.getFileInStream();
        if (inStream == null) {
            return false;
        }
        if (!methodInit(SEND_FILE)) {
            return false;
        }

        if (!sendSize(name_data.length)) {
            return false;
        }
        if (!this.serverConnection.send(name_data)) {
            return false;
        }
        if (!sendSize(fileSize)) {
            return false;
        }
        byte[] buf = new byte[BUF_SZ];
        long sent_sz = 0;
        int progressCurrent;
        if (this.notifier != null) this.notifier.setName("Sending file " + fileName);
        while (fileSize > 0) {
            int read_sz = (int) Math.min(fileSize, BUF_SZ);
            try {
                read_sz = inStream.read(buf, 0, read_sz);
            } catch (IOException ex) {
                return false;
            }
            if (read_sz < 0) {
                return true;
            } else if (read_sz == 0) {
                continue;
            }
            fileSize -= read_sz;
            sent_sz += read_sz;
            if (!this.serverConnection.send(buf, 0, read_sz)) {
                return false;
            }
            progressCurrent = (int) ((sent_sz * 100) / (sent_sz + fileSize));
            if (this.notifier != null) this.notifier.setStatus(progressCurrent);
        }
        if (this.notifier != null) this.notifier.finish();
        return true;
    }

    public boolean getImage() {
        if (!(this.utils instanceof FSUtils)) return false;
        FSUtils fsUtils = (FSUtils) this.utils;
        if (!methodInit(GET_IMAGE)) {
            return false;
        }
        long file_size = readSize();
        if (file_size <= 0) {
            return false;
        }
        OutputStream out = fsUtils.getImageOutStream();
        if (out == null) {
            return false;
        }
        byte[] buf = new byte[BUF_SZ];
        while (file_size > 0) {
            int read_sz = (int) Math.min(file_size, BUF_SZ);
            if (!this.serverConnection.receive(buf, 0, read_sz)) {
                return false;
            }
            file_size -= read_sz;
            try {
                out.write(buf, 0, read_sz);
            } catch (IOException ex) {
                return false;
            }
        }
        try {
            out.close();
            fsUtils.getFileDone("image");
        } catch (IOException ignored) {
        }
        return true;
    }
}
