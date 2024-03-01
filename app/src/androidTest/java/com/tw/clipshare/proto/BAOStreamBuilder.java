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
