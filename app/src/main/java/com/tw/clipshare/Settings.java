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
import java.util.HashMap;
import java.util.List;

public class Settings implements Serializable {

  private static final Object LOCK = new Object();
  private static volatile Settings INSTANCE = null;
  private final List<String> trustedList;
  private boolean secure;
  private byte[] caCert;
  private byte[] cert;
  private char[] passwd;
  private String caCN;
  private String cn;
  private int port;
  private int portSecure;
  private int portUDP;

  private Settings(ArrayList<String> trustedList) {
    this.secure = false;
    this.trustedList = trustedList;
    this.caCert = null;
    this.cert = null;
    this.passwd = null;
    this.cn = null;
    this.caCN = null;
    this.port = 4337;
    this.portSecure = 4338;
    this.portUDP = 4337;
  }

  private Settings() {
    this(new ArrayList<>(1));
  }

  @SuppressWarnings("unchecked")
  private static Settings fromString(String s) throws IOException, ClassNotFoundException {
    byte[] data = Base64.decode(s, Base64.DEFAULT);
    ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data));
    Object o = ois.readObject();
    ois.close();
    if (!(o instanceof HashMap)) throw new RuntimeException();
    HashMap<String, Object> map = (HashMap<String, Object>) o;
    ArrayList<String> trustedList = null;
    try {
      Object trustedListO = map.get("trustedList");
      if (trustedListO instanceof ArrayList) {
        trustedList = (ArrayList<String>) trustedListO;
      }
    } catch (Exception ignored) {
    }

    Settings settings;
    if (trustedList != null) settings = new Settings(trustedList);
    else settings = new Settings();

    // Set caCert
    try {
      Object attributeO = map.get("caCert");
      if (attributeO instanceof byte[]) {
        settings.caCert = (byte[]) attributeO;
      }
    } catch (Exception ignored) {
    }

    // Set cert
    try {
      Object attributeO = map.get("cert");
      if (attributeO instanceof byte[]) {
        settings.cert = (byte[]) attributeO;
      }
    } catch (Exception ignored) {
    }

    // Set passwd
    try {
      Object attributeO = map.get("passwd");
      if (attributeO instanceof char[]) {
        settings.passwd = (char[]) attributeO;
      }
    } catch (Exception ignored) {
    }

    // Set caCN
    try {
      Object attributeO = map.get("caCN");
      if (attributeO instanceof String) {
        settings.caCN = (String) attributeO;
      }
    } catch (Exception ignored) {
    }

    // Set cn
    try {
      Object attributeO = map.get("cn");
      if (attributeO instanceof String) {
        settings.cn = (String) attributeO;
      }
    } catch (Exception ignored) {
    }

    // Set secure
    try {
      Object attributeO = map.get("secure");
      if (attributeO instanceof Boolean) {
        settings.secure = (Boolean) attributeO;
      }
    } catch (Exception ignored) {
    }

    // Set port
    try {
      Object attributeO = map.get("port");
      if (attributeO instanceof Integer) {
        settings.port = (Integer) attributeO;
      }
    } catch (Exception ignored) {
    }

    // Set portSecure
    try {
      Object attributeO = map.get("portSecure");
      if (attributeO instanceof Integer) {
        settings.portSecure = (Integer) attributeO;
      }
    } catch (Exception ignored) {
    }

    // Set portUDP
    try {
      Object attributeO = map.get("portUDP");
      if (attributeO instanceof Integer) {
        settings.portUDP = (Integer) attributeO;
      }
    } catch (Exception ignored) {
    }

    return settings;
  }

  public static String toString(Settings settings) throws IOException {
    HashMap<String, Object> map = new HashMap<>(10);
    map.put("caCert", settings.caCert);
    map.put("cert", settings.cert);
    map.put("passwd", settings.passwd);
    map.put("caCN", settings.caCN);
    map.put("cn", settings.cn);
    map.put("trustedList", settings.trustedList);
    map.put("secure", settings.secure);
    map.put("port", settings.port);
    map.put("portSecure", settings.portSecure);
    map.put("portUDP", settings.portUDP);

    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(byteArrayOutputStream);
    oos.writeObject(map);
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
        INSTANCE.caCN = strSet.caCN;
        INSTANCE.cert = strSet.cert;
        INSTANCE.passwd = strSet.passwd;
        INSTANCE.cn = strSet.cn;
        List<String> thisList = INSTANCE.trustedList;
        thisList.clear();
        thisList.addAll(strSet.trustedList);
        INSTANCE.secure = strSet.secure;
        INSTANCE.port = strSet.port;
        INSTANCE.portSecure = strSet.portSecure;
        INSTANCE.portUDP = strSet.portUDP;
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
    return this.caCN;
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
      this.caCN = caCn;
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
