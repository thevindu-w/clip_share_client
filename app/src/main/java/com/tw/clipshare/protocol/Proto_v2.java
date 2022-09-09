package com.tw.clipshare.protocol;

import com.tw.clipshare.netConnection.ServerConnection;
import com.tw.clipshare.platformUtils.AndroidUtils;
import com.tw.clipshare.platformUtils.FSUtils;
import com.tw.clipshare.platformUtils.StatusNotifier;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class Proto_v2 extends Proto_v1 {

    Proto_v2(ServerConnection serverConnection, AndroidUtils utils, StatusNotifier notifier) {
        super(serverConnection, utils, notifier);
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
            int fileName_len = (int) readSize();
            if (fileName_len <= 0) {
                return false;
            }
            byte[] fileName_data = new byte[fileName_len];
            if (this.serverConnection.receive(fileName_data)) {
                return false;
            }
            String fileName = new String(fileName_data, StandardCharsets.UTF_8);
            long file_size = readSize();
            if (file_size <= 0) {
                return false;
            }
            int base_ind = fileName.lastIndexOf('/') + 1;
            String baseName = fileName.substring(base_ind);
            String path = fileName.substring(0, base_ind);
            OutputStream out = fsUtils.getFileOutStream(path, baseName);
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
        return fsUtils.finish();
    }

    @Override
    public boolean sendFile() {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
