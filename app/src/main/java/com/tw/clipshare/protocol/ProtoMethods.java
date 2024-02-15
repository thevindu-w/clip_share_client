package com.tw.clipshare.protocol;

import com.tw.clipshare.netConnection.ServerConnection;
import com.tw.clipshare.platformUtils.AndroidUtils;
import com.tw.clipshare.platformUtils.FSUtils;
import com.tw.clipshare.platformUtils.StatusNotifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class ProtoMethods {
  private static final byte GET_TEXT = 1;
  private static final byte SEND_TEXT = 2;
  private static final byte GET_FILE = 3;
  private static final byte SEND_FILE = 4;
  private static final byte GET_IMAGE = 5;
  private static final byte INFO = 125;

  private static final byte STATUS_OK = 1;
  private static final int BUF_SZ = 65536;

  private final ServerConnection serverConnection;
  private final AndroidUtils utils;
  private final StatusNotifier notifier;

  ProtoMethods(ServerConnection serverConnection, AndroidUtils utils, StatusNotifier notifier) {
    this.serverConnection = serverConnection;
    this.utils = utils;
    this.notifier = notifier;
  }

  String v1_getText() {
    if (methodInit(GET_TEXT)) {
      return null;
    }
    return readString(4194304);
  }

  boolean v1_sendText(String text) {
    if (text == null) {
      return false;
    }
    if (methodInit(SEND_TEXT)) {
      return false;
    }
    return sendString(text);
  }

  boolean v1_getFile() {
    if (!(this.utils instanceof FSUtils)) return false;
    FSUtils fsUtils = (FSUtils) this.utils;
    if (methodInit(GET_FILE)) {
      return false;
    }
    long fileCnt = readSize();
    for (long fileNum = 0; fileNum < fileCnt; fileNum++) {
      String fileName = readString(2048);
      if (fileName == null || fileName.isEmpty() || fileName.contains("/")) {
        return false;
      }
      long file_size = readSize();
      if (file_size < 0 || file_size > 17179869184L) { // limit the file size to 16 GiB
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

  boolean v1_sendFile() {
    if (!(this.utils instanceof FSUtils)) return false;
    FSUtils fsUtils = (FSUtils) this.utils;
    if (!fsUtils.prepareNextFile()) return false;
    String fileName = fsUtils.getFileName();
    if (fileName == null || fileName.isEmpty()) {
      return false;
    }
    long fileSize = fsUtils.getFileSize();
    if (fileSize < 0) {
      return false;
    }
    InputStream inStream = fsUtils.getFileInStream();
    if (inStream == null) {
      return false;
    }
    if (methodInit(SEND_FILE)) {
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

  boolean v1_getImage() {
    if (!(this.utils instanceof FSUtils)) return false;
    FSUtils fsUtils = (FSUtils) this.utils;
    if (methodInit(GET_IMAGE)) {
      return false;
    }
    long file_size = readSize();
    if (file_size <= 0 || file_size > 268435456) { // limit the image size to 256 MiB
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

  private long readSize() {
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

  String v1_checkInfo() {
    if (methodInit(INFO)) {
      return null;
    }
    try {
      String info = readString(2048);
      if (info == null || info.isEmpty()) {
        return null;
      }
      return info;
    } catch (Exception ignored) {
      return null;
    }
  }

  boolean v2_getFile() {
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

  boolean v2_sendFile() {
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

  private boolean sendSize(long size) {
    byte[] data = new byte[8];
    for (int i = data.length - 1; i >= 0; i--) {
      data[i] = (byte) (size & 0xFF);
      size >>= 8;
    }
    return !this.serverConnection.send(data);
  }

  private boolean methodInit(byte method) {
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
  private String readString(int maxSize) {
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
  private boolean sendString(String data) {
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

  /** Close the connection used for communicating with the server */
  public void close() {
    try {
      if (this.serverConnection != null) this.serverConnection.close();
    } catch (Exception ignored) {
    }
  }
}
