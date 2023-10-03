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

package com.tw.clipshare;

import android.util.Base64;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class Settings implements Serializable {

  private static final Object LOCK = new Object();
  private static volatile Settings INSTANCE = null;
  private final List<String> trustedList;
  private boolean secure;
  private byte[] caCert;
  private byte[] cert;
  private char[] passwd;
  private String caCn;
  private String cn;
  private int port;
  private int portSecure;
  private int portUDP;

  private Settings() {
    this.secure = false;
    this.trustedList = new ArrayList<>(1);
    this.caCert = null;
    this.cert = null;
    this.passwd = null;
    this.cn = null;
    this.port = 4337;
    this.portSecure = 4338;
    this.portUDP = 4337;
  }

  private static Settings fromString(String s) throws IOException, ClassNotFoundException {
    byte[] data = Base64.decode(s, Base64.DEFAULT);
    ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data));
    Object o = ois.readObject();
    ois.close();
    return (Settings) o;
  }

  public static String toString(Serializable o) throws IOException {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(byteArrayOutputStream);
    oos.writeObject(o);
    oos.close();
    return Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.DEFAULT);
  }

  public static Settings getInstance(String data) {
    if (Settings.INSTANCE == null) {
      synchronized (Settings.LOCK) {
        if (Settings.INSTANCE == null) {
          Settings.INSTANCE = new Settings();
        }
      }
    }
    if (data != null) {
      try {
        Settings strSet = fromString(data);
        INSTANCE.caCert = strSet.caCert;
        INSTANCE.caCn = strSet.caCn;
        INSTANCE.cert = strSet.cert;
        INSTANCE.passwd = strSet.passwd;
        INSTANCE.cn = strSet.cn;
        List<String> thisList = INSTANCE.trustedList;
        thisList.clear();
        thisList.addAll(strSet.trustedList);
        INSTANCE.secure = strSet.secure;
        INSTANCE.port = strSet.port;
        INSTANCE.portSecure = strSet.portSecure;
      } catch (Exception ignored) {
      }
    }
    return Settings.INSTANCE;
  }

  public List<String> getTrustedList() {
    return this.trustedList;
  }

  public boolean getSecure() {
    return secure;
  }

  public InputStream getCACertInputStream() {
    return new ByteArrayInputStream(this.caCert);
  }

  public InputStream getCertInputStream() {
    return new ByteArrayInputStream(this.cert);
  }

  public char[] getPasswd() {
    return passwd == null ? null : passwd.clone();
  }

  public String getCACertCN() {
    return this.caCn;
  }

  public String getCertCN() {
    return this.cn;
  }

  public int getPort() {
    return port;
  }

  public int getPortSecure() {
    return portSecure;
  }

  public int getPortUDP() {
    return portUDP;
  }

  public void setSecure(boolean secure) {
    this.secure = secure;
  }

  public String setCertPass(char[] passwd, InputStream certIn, int certLen) {
    try {
      byte[] buf = new byte[certLen];
      {
        int readLen = 0;
        do {
          int read = certIn.read(buf, readLen, certLen - readLen);
          readLen += read;
        } while (readLen < certLen);
      }
      certIn.close();
      String cn = CertUtils.getCertCN(passwd, new ByteArrayInputStream(buf));
      if (cn == null) return null;
      this.cert = buf;
      this.passwd = passwd;
      this.cn = cn;
      return cn;
    } catch (Exception ignored) {
      return null;
    }
  }

  public String setCACert(InputStream certIn, int certLen) {
    try {
      byte[] buf = new byte[certLen];
      {
        int readLen = 0;
        do {
          int read = certIn.read(buf, readLen, certLen - readLen);
          readLen += read;
        } while (readLen < certLen);
      }
      certIn.close();
      String caCn =
          CertUtils.getCertCN(CertUtils.getX509fromInputStream(new ByteArrayInputStream(buf)));
      if (caCn == null) return null;
      this.caCert = buf;
      this.caCn = caCn;
      return caCn;
    } catch (Exception ignored) {
      return null;
    }
  }

  public void setPort(int port) {
    this.port = port;
  }

  public void setPortUDP(int port) {
    this.portUDP = port;
  }

  public void setPortSecure(int port) {
    this.portSecure = port;
  }
}
