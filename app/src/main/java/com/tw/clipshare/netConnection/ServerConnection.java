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
