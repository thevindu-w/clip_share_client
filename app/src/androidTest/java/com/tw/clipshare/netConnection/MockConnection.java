package com.tw.clipshare.netConnection;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MockConnection extends ServerConnection {
  public MockConnection(InputStream inputStream, OutputStream outputStream) {
    super();
    this.inStream = inputStream;
    this.outStream = outputStream;
  }

  public MockConnection(InputStream inputStream) {
    this(inputStream, new ByteArrayOutputStream());
  }

  public byte[] getOutputBytes() {
    ByteArrayOutputStream ostream = (ByteArrayOutputStream) this.outStream;
    return ostream.toByteArray();
  }
}
