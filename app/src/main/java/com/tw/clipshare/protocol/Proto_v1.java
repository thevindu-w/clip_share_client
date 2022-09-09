package com.tw.clipshare.protocol;

import com.tw.clipshare.netConnection.ServerConnection;
import com.tw.clipshare.platformUtils.AndroidUtils;
import com.tw.clipshare.platformUtils.FSUtils;
import com.tw.clipshare.platformUtils.StatusNotifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class Proto_v1 extends Proto {

    Proto_v1(ServerConnection serverConnection, AndroidUtils utils, StatusNotifier notifier) {
        super(serverConnection, utils, notifier);
    }

    @Override
    public String getText() {
        if (methodInit(GET_TEXT)) {
            return null;
        }
        byte[] data = readData();
        if (data == null) {
            return null;
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    @Override
    public boolean sendText(String text) {
        if (text == null) {
            return false;
        }
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        if (methodInit(SEND_TEXT)) {
            return false;
        }

        return sendData(data);
    }

    @Override
    public boolean getFile() {
        if (!(this.utils instanceof FSUtils)) return false;
        FSUtils fsUtils = (FSUtils) this.utils;
        if (methodInit(GET_FILE)) {
            return false;
        }
        long fileCnt = readSize();
        for (long fileNum = 0; fileNum < fileCnt; fileNum++) {
            byte[] fileName_data = readData();
            if (fileName_data == null) {
                return false;
            }
            String fileName = new String(fileName_data, StandardCharsets.UTF_8);
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
                if (this.serverConnection.receive(buf, 0, read_sz)) {
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

    @Override
    public boolean sendFile() {
        if (!(this.utils instanceof FSUtils)) return false;
        FSUtils fsUtils = (FSUtils) this.utils;
        String fileName = fsUtils.getFileName();
        if (fileName == null) {
            return false;
        }
        byte[] name_data = fileName.getBytes(StandardCharsets.UTF_8);
        long fileSize = fsUtils.getFileSize();
        if (fileSize <= 0) {
            return false;
        }
        InputStream inStream = fsUtils.getFileInStream();
        if (inStream == null) {
            return false;
        }
        if (methodInit(SEND_FILE)) {
            return false;
        }

        if (!sendData(name_data)) {
            return false;
        }
        if (sendSize(fileSize)) {
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

    @Override
    public boolean getImage() {
        if (!(this.utils instanceof FSUtils)) return false;
        FSUtils fsUtils = (FSUtils) this.utils;
        if (methodInit(GET_IMAGE)) {
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
            if (this.serverConnection.receive(buf, 0, read_sz)) {
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

    @Override
    public String checkInfo() {
        if (methodInit(INFO)) {
            return null;
        }
        try {
            byte[] data = readData();
            if (data == null) {
                return null;
            }
            return new String(data, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return null;
        }
    }
}
