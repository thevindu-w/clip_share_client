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

package com.tw.clipshare.proto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class BAOStreamBuilder {
  private final ByteArrayOutputStream baoStream;

  BAOStreamBuilder() {
    this.baoStream = new ByteArrayOutputStream();
  }

  public ByteArrayInputStream getStream() {
    return new ByteArrayInputStream(this.baoStream.toByteArray());
  }

  public byte[] getArray() {
    return this.baoStream.toByteArray();
  }

  public void addByte(int b) {
    this.baoStream.write(b);
  }

  public void addBytes(byte[] array) throws IOException {
    this.baoStream.write(array);
  }

  public void addSize(long size) throws IOException {
    byte[] data = new byte[8];
    for (int i = data.length - 1; i >= 0; i--) {
      data[i] = (byte) (size & 0xFF);
      size >>= 8;
    }
    this.baoStream.write(data);
  }

  public void addString(String str) throws IOException {
    byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
    addData(bytes);
  }

  public void addData(byte[] data) throws IOException {
    addSize(data.length);
    this.baoStream.write(data);
  }
}
