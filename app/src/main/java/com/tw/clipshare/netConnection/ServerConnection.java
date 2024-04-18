/*
 * MIT License
 *
 * Copyright (c) 2022-2024 H. Thevindu J. Wijesekera
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
  private boolean closed;

  protected ServerConnection() {
    this.socket = null;
    this.closed = false;
  }

  protected ServerConnection(Socket socket) {
    this.socket = socket;
  }

  /**
   * Sends length bytes of data from buffer starting at offset to server.
   *
   * @param buffer buffer containing data, which should be at least offset+length in size
   * @param offset index of starting byte of buffer
   * @param length number of bytes to send
   * @return false on success or true on failure
   */
  public boolean send(byte[] buffer, int offset, int length) {
    try {
      outStream.write(buffer, offset, length);
      return false;
    } catch (RuntimeException | IOException ex) {
      return true;
    }
  }

  /**
   * Receives length bytes of data from server and stores it in buffer starting at offset
   *
   * @param buffer buffer to store data, which should be at least offset+length in size
   * @param offset index of starting byte in buffer
   * @param length number of bytes to read
   * @return false on success or true on failure
   */
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

  /**
   * Sends all data in buffer to server.
   *
   * @param buffer buffer containing data
   * @return false on success or true on failure
   */
  public boolean send(byte[] buffer) {
    return this.send(buffer, 0, buffer.length);
  }

  /**
   * Receives data into buffer from server until buffer is full.
   *
   * @param buffer buffer to store data
   * @return false on success or true on failure
   */
  public boolean receive(byte[] buffer) {
    return this.receive(buffer, 0, buffer.length);
  }

  public void close() {
    synchronized (this) {
      if (this.closed) return;
      this.closed = true;
    }
    try {
      this.socket.close();
    } catch (RuntimeException | IOException ignored) {
    }
  }

  @Override
  protected void finalize() throws Throwable {
    this.close();
    super.finalize();
  }
}
