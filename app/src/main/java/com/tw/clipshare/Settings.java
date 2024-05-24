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

package com.tw.clipshare;

import android.util.Base64;
import androidx.annotation.NonNull;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
  private boolean autoSendText;
  private boolean autoSendFiles;
  private boolean vibrate;

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
    this.autoSendText = false;
    this.autoSendFiles = false;
    this.vibrate = true;
  }

  private Settings() {
    this(new ArrayList<>(1));
  }

  private static Settings fromString(String s) throws JSONException {
    JSONObject map = new JSONObject(s);
    ArrayList<String> trustedList = null;
    try {
      Object trustedListO = map.get("trustedList");
      if (trustedListO instanceof JSONArray) {
        JSONArray jsonArray = (JSONArray) trustedListO;
        int len = jsonArray.length();
        trustedList = new ArrayList<>(len);
        for (int i = 0; i < len; i++) {
          trustedList.add(jsonArray.getString(i));
        }
      }
    } catch (Exception ignored) {
      trustedList = null;
    }

    Settings settings;
    if (trustedList != null) settings = new Settings(trustedList);
    else settings = new Settings();

    // Set caCert
    try {
      Object attributeO = map.get("caCert");
      if (attributeO instanceof String) {
        settings.caCert = Base64.decode((String) attributeO, Base64.DEFAULT);
      }
    } catch (Exception ignored) {
    }

    // Set cert
    try {
      Object attributeO = map.get("cert");
      if (attributeO instanceof String) {
        settings.cert = Base64.decode((String) attributeO, Base64.DEFAULT);
      }
    } catch (Exception ignored) {
    }

    // Set passwd
    try {
      Object attributeO = map.get("passwd");
      if (attributeO instanceof String) {
        String pwStr = (String) attributeO;
        settings.passwd = pwStr.toCharArray();
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

    // Set autoSendText
    try {
      Object attributeO = map.get("autoSendText");
      if (attributeO instanceof Boolean) {
        settings.autoSendText = (Boolean) attributeO;
      }
    } catch (Exception ignored) {
    }

    // Set autoSendFiles
    try {
      Object attributeO = map.get("autoSendFiles");
      if (attributeO instanceof Boolean) {
        settings.autoSendFiles = (Boolean) attributeO;
      }
    } catch (Exception ignored) {
    }

    // Set vibrate
    try {
      Object attributeO = map.get("vibrate");
      if (attributeO instanceof Boolean) {
        settings.vibrate = (Boolean) attributeO;
      }
    } catch (Exception ignored) {
    }

    return settings;
  }

  @NonNull
  @Override
  public String toString() {
    return this.toString(true);
  }

  @NonNull
  public String toString(boolean includePassword) {
    HashMap<String, Object> map = new HashMap<>(13);
    try {
      if (this.caCert != null)
        map.put("caCert", Base64.encodeToString(this.caCert, Base64.DEFAULT));
      if (this.cert != null) map.put("cert", Base64.encodeToString(this.cert, Base64.DEFAULT));
      if (includePassword && this.passwd != null) map.put("passwd", new String(this.passwd));
      map.put("caCN", this.caCN);
      map.put("cn", this.cn);
      map.put("trustedList", this.trustedList);
      map.put("secure", this.secure);
      map.put("port", this.port);
      map.put("portSecure", this.portSecure);
      map.put("portUDP", this.portUDP);
      map.put("autoSendText", this.autoSendText);
      map.put("autoSendFiles", this.autoSendFiles);
      map.put("vibrate", this.vibrate);
    } catch (Exception ignored) {
    }
    JSONObject jsonObject = new JSONObject(map);
    return jsonObject.toString();
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
        INSTANCE.autoSendText = strSet.autoSendText;
        INSTANCE.autoSendFiles = strSet.autoSendFiles;
        INSTANCE.vibrate = strSet.vibrate;
      } catch (Exception ignored) {
      }
    }
    return Settings.INSTANCE;
  }

  public static Settings getInstance() {
    return Settings.getInstance(null);
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

  public boolean getAutoSendText() {
    return autoSendText;
  }

  public boolean getAutoSendFiles() {
    return autoSendFiles;
  }

  public boolean getVibrate() {
    return vibrate;
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

  public void setAutoSendText(boolean autoSendText) {
    this.autoSendText = autoSendText;
  }

  public void setAutoSendFiles(boolean autoSendFiles) {
    this.autoSendFiles = autoSendFiles;
  }

  public void setVibrate(boolean vibrate) {
    this.vibrate = vibrate;
  }
}
