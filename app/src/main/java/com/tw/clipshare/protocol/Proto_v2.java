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
import com.tw.clipshare.platformUtils.FSUtils;
import com.tw.clipshare.platformUtils.StatusNotifier;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

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
      String fileName = readString(2048);
      if (fileName == null || fileName.isEmpty()) {
        return false;
      }
      long file_size = readSize();
      if (file_size < 0 || file_size > 17179869184L) { // limit the file size to 16 GiB
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
      if (this.notifier != null) {
        this.notifier.reset();
        this.notifier.setName("Getting file " + fileName);
      }
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
    if (!(this.utils instanceof FSUtils)) return false;
    FSUtils fsUtils = (FSUtils) this.utils;
    int fileCnt = fsUtils.getRemainingFileCount();
    if (fileCnt <= 0) return false;
    if (methodInit(SEND_FILE)) {
      return false;
    }
    try {
      if (sendSize(fileCnt)) {
        return false;
      }
      for (int fileNum = 0; fileNum < fileCnt; fileNum++) {
        fsUtils.prepareNextFile();
        String fileName = fsUtils.getFileName();
        long fileSize = fsUtils.getFileSize();
        InputStream inStream = fsUtils.getFileInStream();
        if (fileSize == -1) {
          ArrayList<Byte> tmpList = new ArrayList<>(8192);
          byte[] tmpArray = new byte[8192];
          while (true) {
            int read = inStream.read(tmpArray);
            if (read < 0) break;
            List<Byte> boxed = new ArrayList<>(read);
            for (int i = 0; i < read; i++) {
              boxed.add(tmpArray[i]);
            }
            tmpList.addAll(boxed);
            if (tmpList.size() > 16777216) throw new Exception("Cannot determine file size");
          }
          byte[] bytes = new byte[tmpList.size()];
          for (int i = 0; i < bytes.length; i++) {
            bytes[i] = tmpList.get(i);
          }
          fileSize = bytes.length;
          inStream = new ByteArrayInputStream(bytes);
        }
        if (fileName == null || fileName.isEmpty()) {
          return false;
        }
        if (fileSize < 0) {
          return false;
        }
        if (inStream == null) {
          return false;
        }
        if (!sendString(fileName)) {
          return false;
        }
        if (sendSize(fileSize)) {
          return false;
        }
        byte[] buf = new byte[BUF_SZ];
        long sent_sz = 0;
        int progressCurrent;
        if (this.notifier != null) {
          this.notifier.reset();
          this.notifier.setName("Sending file " + fileName);
        }
        while (fileSize > 0) {
          int read_sz = (int) Math.min(fileSize, BUF_SZ);
          try {
            read_sz = inStream.read(buf, 0, read_sz);
          } catch (IOException ex) {
            return false;
          }
          if (read_sz < 0) {
            return false;
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
      }
    } catch (Exception ignored) {
      return false;
    }
    if (this.notifier != null) this.notifier.finish();
    return true;
  }
}
