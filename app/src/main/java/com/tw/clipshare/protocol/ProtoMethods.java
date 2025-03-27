/*
 * MIT License
 *
 * Copyright (c) 2022-2025 H. Thevindu J. Wijesekera
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
import com.tw.clipshare.platformUtils.DataContainer;
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
  private static final int MAX_TEXT_LENGTH = 4194304; // 4 MiB
  private static final int MAX_FILE_NAME_LENGTH = 2048;
  private static final long MAX_FILE_SIZE = 17179869184L; // 16 GiB
  private static final long MAX_IMAGE_SIZE = 268435456; // 256 MiB
  private static final byte GET_TEXT = 1;
  private static final byte SEND_TEXT = 2;
  private static final byte GET_FILE = 3;
  private static final byte SEND_FILE = 4;
  private static final byte GET_IMAGE = 5;
  private static final byte GET_COPIED_IMAGE = 6;
  private static final byte GET_SCREENSHOT = 7;
  private static final byte INFO = 125;

  private static final byte STATUS_OK = 1;
  private static final int BUF_SZ = 65536;

  private final ServerConnection serverConnection;
  private final AndroidUtils utils;
  private StatusNotifier notifier;
  private final DataContainer dataContainer;
  private volatile boolean isRunning;

  ProtoMethods(
      ServerConnection serverConnection,
      AndroidUtils utils,
      StatusNotifier notifier,
      DataContainer dataContainer) {
    this.serverConnection = serverConnection;
    this.utils = utils;
    this.notifier = notifier;
    this.dataContainer = dataContainer;
    this.isRunning = true;
  }

  boolean v1_getText() {
    if (methodInit(GET_TEXT)) {
      return false;
    }
    String data = readString(MAX_TEXT_LENGTH);
    if (data == null) return false;
    dataContainer.setData(data);
    return true;
  }

  boolean v1_sendText(String text) {
    if (text == null) {
      return false;
    }
    if (methodInit(SEND_TEXT)) {
      return false;
    }
    return !sendString(text);
  }

  boolean v1_getFiles() {
    return getFilesCommon(1);
  }

  boolean v1_sendFile() {
    if (!(this.utils instanceof FSUtils fsUtils)) return false;
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
    if (sendString(fileName)) {
      return false;
    }
    if (sendSize(fileSize)) {
      return false;
    }
    byte[] buf = new byte[BUF_SZ];
    long sent_sz = 0;
    if (this.notifier != null) {
      this.notifier.setTitle(fileName);
      this.notifier.setFileSize(fileSize);
    }
    while (fileSize > 0 && isRunning) {
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
      if (this.serverConnection.send(buf, 0, read_sz)) {
        return false;
      }
      sent_sz += read_sz;
      if (this.notifier != null) this.notifier.setProgress(sent_sz);
    }
    return isRunning;
  }

  private boolean getImageCommon(byte method, int display) {
    if (!(this.utils instanceof FSUtils fsUtils)) return false;
    if (methodInit(method)) {
      return false;
    }
    if (method == GET_SCREENSHOT && selectDisplay(display)) return false;
    long fileSize;
    try {
      fileSize = readSize();
    } catch (IOException ignored) {
      return false;
    }
    if (fileSize <= 0 || fileSize > MAX_IMAGE_SIZE) {
      return false;
    }
    OutputStream out = fsUtils.getImageOutStream();
    if (out == null) {
      return false;
    }
    byte[] buf = new byte[BUF_SZ];
    while (fileSize > 0) {
      int read_sz = (int) Math.min(fileSize, BUF_SZ);
      if (this.serverConnection.receive(buf, 0, read_sz)) {
        return false;
      }
      fileSize -= read_sz;
      try {
        out.write(buf, 0, read_sz);
      } catch (IOException ex) {
        return false;
      }
    }
    try {
      out.close();
      fsUtils.setDataContainer(dataContainer);
      fsUtils.getFileDone("image");
      fsUtils.scanMediaFile();
    } catch (IOException ignored) {
    }
    return true;
  }

  private boolean getImageCommon(byte method) {
    return getImageCommon(method, 0);
  }

  boolean v1_getImage() {
    return getImageCommon(GET_IMAGE);
  }

  String v1_checkInfo() {
    if (methodInit(INFO)) {
      return null;
    }
    try {
      String info = readString(MAX_FILE_NAME_LENGTH);
      if (info == null || info.isEmpty()) {
        return null;
      }
      return info;
    } catch (Exception ignored) {
      return null;
    }
  }

  private boolean getFilesCommon(int version) {
    try {
      if (!(this.utils instanceof FSUtils fsUtils)) return false;
      if (methodInit(GET_FILE)) {
        return false;
      }
      long fileCnt = readSize();
      boolean status = true;
      for (long fileNum = 0; fileNum < fileCnt && isRunning; fileNum++) {
        String fileName = readString(MAX_FILE_NAME_LENGTH);
        if (fileName == null || fileName.isEmpty()) {
          status = false;
          break;
        }
        if (version == 1 && fileName.contains("/")) {
          status = false;
          break;
        }
        long fileSize;
        try {
          fileSize = readSize();
        } catch (IOException ignored) {
          status = false;
          break;
        }
        if (fileSize > MAX_FILE_SIZE) {
          status = false;
          break;
        }
        if (version == 3 && fileSize < 0) {
          status &= fsUtils.createDirectory(fileName);
          continue;
        } else if (fileSize < 0) {
          status = false;
          break;
        }
        OutputStream out = fsUtils.getFileOutStream(fileName);
        if (out == null) {
          status = false;
          break;
        }
        byte[] buf = new byte[BUF_SZ];
        long received = 0;
        if (this.notifier != null) {
          this.notifier.reset();
          this.notifier.setTitle(fileName);
          this.notifier.setFileSize(fileSize);
        }
        while (fileSize > 0 && isRunning) {
          int read_sz = (int) Math.min(fileSize, BUF_SZ);
          if (this.serverConnection.receive(buf, 0, read_sz)) {
            status = false;
            break;
          }
          fileSize -= read_sz;
          received += read_sz;
          try {
            out.write(buf, 0, read_sz);
          } catch (IOException ex) {
            status = false;
            break;
          }
          if (this.notifier != null) this.notifier.setProgress(received);
        }
        try {
          out.close();
        } catch (IOException ignored) {
        }
        if (!status) break;
      }
      fsUtils.setDataContainer(dataContainer);
      status = status && isRunning && fsUtils.finish();
      if (status) fsUtils.getFileDone("files");
      return status;
    } catch (Exception ignored) {
      return false;
    }
  }

  boolean v2_getFiles() {
    return getFilesCommon(2);
  }

  boolean sendFilesCommon(int version) {
    if (!(this.utils instanceof FSUtils fsUtils)) return false;
    int fileCnt = fsUtils.getRemainingFileCount(version >= 3);
    if (fileCnt <= 0) return false;
    if (methodInit(SEND_FILE)) return false;
    try {
      if (sendSize(fileCnt)) return false;
      for (int fileNum = 0; fileNum < fileCnt && isRunning; fileNum++) {
        fsUtils.prepareNextFile(version >= 3);
        String fileName = fsUtils.getFileName();
        if (fileName == null || fileName.isEmpty()) return false;
        long fileSize = fsUtils.getFileSize();
        InputStream inStream = fsUtils.getFileInStream();
        if (fileSize == -1 && inStream != null) {
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
        if (fileSize < 0) {
          if (version == 2) return false;
          if (version == 3) {
            if (sendString(fileName)) return false;
            if (sendSize(fileSize)) return false;
            continue;
          }
        }
        if (inStream == null) return false;
        if (sendString(fileName)) return false;
        if (sendSize(fileSize)) return false;
        byte[] buf = new byte[BUF_SZ];
        long sent_sz = 0;
        if (this.notifier != null) {
          this.notifier.reset();
          this.notifier.setTitle(fileName);
          this.notifier.setFileSize(fileSize);
        }
        while (fileSize > 0 && isRunning) {
          int read_sz = (int) Math.min(fileSize, BUF_SZ);
          try {
            read_sz = inStream.read(buf, 0, read_sz);
          } catch (IOException ex) {
            return false;
          }
          if (read_sz < 0) return false;
          else if (read_sz == 0) continue;
          fileSize -= read_sz;
          sent_sz += read_sz;
          if (this.serverConnection.send(buf, 0, read_sz)) return false;
          if (this.notifier != null) this.notifier.setProgress(sent_sz);
        }
      }
    } catch (Exception ignored) {
      return false;
    }
    return isRunning;
  }

  boolean v2_sendFiles() {
    return sendFilesCommon(2);
  }

  boolean v3_getFiles() {
    return getFilesCommon(3);
  }

  boolean v3_sendFiles() {
    return sendFilesCommon(3);
  }

  boolean v3_getCopiedImage() {
    return getImageCommon(GET_COPIED_IMAGE);
  }

  private boolean selectDisplay(int display) {
    if (sendSize(display)) return true;
    byte[] status = new byte[1];
    return (this.serverConnection.receive(status) || status[0] != STATUS_OK);
  }

  boolean v3_getScreenshot(int display) {
    return getImageCommon(GET_SCREENSHOT, display);
  }

  /**
   * Reads a 64-bit signed integer from server
   *
   * @throws IOException on failure
   * @return integer received
   */
  private long readSize() throws IOException {
    byte[] data = new byte[8];
    if (this.serverConnection.receive(data)) {
      throw new IOException();
    }
    long size = 0;
    for (byte b : data) {
      size = (size << 8) | (b & 0xFF);
    }
    return size;
  }

  /**
   * Sends a 64-bit signed integer to server
   *
   * @param size value to be sent
   * @return false on success or true on error
   */
  private boolean sendSize(long size) {
    byte[] data = new byte[8];
    for (int i = data.length - 1; i >= 0; i--) {
      data[i] = (byte) (size & 0xFF);
      size >>= 8;
    }
    return this.serverConnection.send(data);
  }

  /**
   * Initializes the method
   *
   * @param method method code
   * @return false on success or true on failure
   */
  private boolean methodInit(byte method) {
    byte[] methodArr = {method};
    if (this.serverConnection.send(methodArr)) {
      return true;
    }
    byte[] status = new byte[1];
    return (this.serverConnection.receive(status) || status[0] != STATUS_OK);
  }

  /**
   * Reads a non-empty String, encoded with UTF-8, from server
   *
   * @param maxSize maximum size to read
   * @return read string or null on error
   */
  private String readString(int maxSize) {
    long size;
    try {
      size = this.readSize();
    } catch (IOException ignored) {
      return null;
    }
    if (size <= 0 || size > maxSize) {
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
   * @return false on success or true on error
   */
  private boolean sendString(String data) {
    if (data == null) return true;
    final byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
    final int len = bytes.length;
    if (len >= 16777216) return true;
    if (this.sendSize(len)) return true;
    return this.serverConnection.send(bytes);
  }

  public void setStatusNotifier(StatusNotifier notifier) {
    try {
      if (this.notifier != null) this.notifier.finish();
    } catch (Exception ignored) {
    }
    this.notifier = notifier;
  }

  public void requestStop() {
    this.isRunning = false;
  }

  public boolean isStopped() {
    return !isRunning;
  }

  /** Close the connection used for communicating with the server */
  public void close() {
    try {
      if (this.serverConnection != null) this.serverConnection.close();
    } catch (Exception ignored) {
    }
  }
}
