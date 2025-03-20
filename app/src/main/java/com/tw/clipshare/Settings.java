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
  private static final Object LOAD_LOCK = new Object();

  private static volatile boolean isSettingsLoaded = false;
  private static volatile Settings INSTANCE = null;
  private final List<String> trustedList;
  private final List<String> autoSendTrustedList;
  private final List<String> savedServersList;
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
  private boolean scanIPv6;
  private boolean closeIfIdle;
  private int autoCloseDelay;
  private boolean saveServers;

  private Settings() {
    this.secure = false;
    this.trustedList = new ArrayList<>(1);
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
    this.autoSendTrustedList = new ArrayList<>(1);
    this.savedServersList = new ArrayList<>(1);
    this.vibrate = true;
    this.scanIPv6 = true;
    this.closeIfIdle = true;
    this.autoCloseDelay = 120;
    this.saveServers = true;
  }

  private static ArrayList<String> objectToArrayList(Object listO) {
    try {
      ArrayList<String> list = null;
      if (listO instanceof JSONArray) {
        JSONArray jsonArray = (JSONArray) listO;
        int len = jsonArray.length();
        list = new ArrayList<>(len);
        for (int i = 0; i < len; i++) {
          String item = jsonArray.getString(i);
          if (item.isEmpty() || item.length() > 256) continue;
          list.add(item);
        }
      }
      if (list != null && !list.isEmpty()) {
        return list;
      }
    } catch (Exception ignored) {
    }
    return null;
  }

  private static Settings fromString(String s) throws JSONException {
    JSONObject map = new JSONObject(s);

    Settings settings = new Settings();

    // Set trustedList
    try {
      ArrayList<String> trustedList = objectToArrayList(map.get("trustedList"));
      if (trustedList != null) {
        settings.trustedList.clear();
        settings.trustedList.addAll(trustedList);
      }
    } catch (Exception ignored) {
    }

    // Set autoSendTrustedList
    try {
      ArrayList<String> autoSendTrustedList = objectToArrayList(map.get("autoSendTrustedList"));
      if (autoSendTrustedList != null) {
        settings.autoSendTrustedList.clear();
        settings.autoSendTrustedList.addAll(autoSendTrustedList);
      }
    } catch (Exception ignored) {
    }

    // Set savedServersList
    try {
      ArrayList<String> savedServersList = objectToArrayList(map.get("savedServersList"));
      if (savedServersList != null) {
        settings.savedServersList.clear();
        settings.savedServersList.addAll(savedServersList);
      }
    } catch (Exception ignored) {
    }

    // Set caCert
    try {
      Object attributeO = map.get("caCert");
      if (attributeO instanceof String) {
        String value = (String) attributeO;
        if (!value.isEmpty() && value.length() < 32768)
          settings.caCert = Base64.decode(value, Base64.DEFAULT);
      }
    } catch (Exception ignored) {
    }

    // Set cert
    try {
      Object attributeO = map.get("cert");
      if (attributeO instanceof String) {
        String value = (String) attributeO;
        if (!value.isEmpty() && value.length() < 32768)
          settings.cert = Base64.decode(value, Base64.DEFAULT);
      }
    } catch (Exception ignored) {
    }

    // Set passwd
    try {
      Object attributeO = map.get("passwd");
      if (attributeO instanceof String) {
        String pwStr = (String) attributeO;
        if (!pwStr.isEmpty() && pwStr.length() < 256) settings.passwd = pwStr.toCharArray();
      }
    } catch (Exception ignored) {
    }

    // Set caCN
    try {
      Object attributeO = map.get("caCN");
      if (attributeO instanceof String) {
        String value = (String) attributeO;
        if (!value.isEmpty() && value.length() < 256) settings.caCN = value;
      }
    } catch (Exception ignored) {
    }

    // Set cn
    try {
      Object attributeO = map.get("cn");
      if (attributeO instanceof String) {
        String value = (String) attributeO;
        if (!value.isEmpty() && value.length() < 256) settings.cn = value;
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
        int value = (Integer) attributeO;
        if (0 < value && value < 65536) settings.port = value;
      }
    } catch (Exception ignored) {
    }

    // Set portSecure
    try {
      Object attributeO = map.get("portSecure");
      if (attributeO instanceof Integer) {
        int value = (Integer) attributeO;
        if (0 < value && value < 65536) settings.portSecure = value;
      }
    } catch (Exception ignored) {
    }

    // Set portUDP
    try {
      Object attributeO = map.get("portUDP");
      if (attributeO instanceof Integer) {
        int value = (Integer) attributeO;
        if (0 < value && value < 65536) settings.portUDP = value;
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

    // Set scanIPv6
    try {
      Object attributeO = map.get("scanIPv6");
      if (attributeO instanceof Boolean) {
        settings.scanIPv6 = (Boolean) attributeO;
      }
    } catch (Exception ignored) {
    }

    // Set closeIfIdle
    try {
      Object attributeO = map.get("closeIfIdle");
      if (attributeO instanceof Boolean) {
        settings.closeIfIdle = (Boolean) attributeO;
      }
    } catch (Exception ignored) {
    }

    // Set autoCloseDelay
    try {
      Object attributeO = map.get("autoCloseDelay");
      if (attributeO instanceof Integer) {
        int value = (Integer) attributeO;
        if (0 < value && value < 10000) settings.autoCloseDelay = value;
      }
    } catch (Exception ignored) {
    }

    // Set saveServers
    try {
      Object attributeO = map.get("saveServers");
      if (attributeO instanceof Boolean) {
        settings.saveServers = (Boolean) attributeO;
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
    HashMap<String, Object> map = new HashMap<>(19);
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
      map.put("autoSendTrustedList", this.autoSendTrustedList);
      map.put("vibrate", this.vibrate);
      map.put("scanIPv6", this.scanIPv6);
      map.put("closeIfIdle", this.closeIfIdle);
      map.put("autoCloseDelay", this.autoCloseDelay);
      map.put("savedServersList", this.savedServersList);
      map.put("saveServers", this.saveServers);
    } catch (Exception ignored) {
    }

    JSONObject jsonObject = new JSONObject(map);
    return jsonObject.toString();
  }

  public static void loadInstance(String data) {
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
        INSTANCE.trustedList.clear();
        INSTANCE.trustedList.addAll(strSet.trustedList);
        INSTANCE.secure = strSet.secure;
        INSTANCE.port = strSet.port;
        INSTANCE.portSecure = strSet.portSecure;
        INSTANCE.portUDP = strSet.portUDP;
        INSTANCE.autoSendText = strSet.autoSendText;
        INSTANCE.autoSendFiles = strSet.autoSendFiles;
        INSTANCE.autoSendTrustedList.clear();
        INSTANCE.autoSendTrustedList.addAll(strSet.autoSendTrustedList);
        INSTANCE.vibrate = strSet.vibrate;
        INSTANCE.scanIPv6 = strSet.scanIPv6;
        INSTANCE.closeIfIdle = strSet.closeIfIdle;
        INSTANCE.autoCloseDelay = strSet.autoCloseDelay;
        INSTANCE.savedServersList.clear();
        INSTANCE.savedServersList.addAll(strSet.savedServersList);
        INSTANCE.saveServers = strSet.saveServers;
      } catch (Exception ignored) {
      }
    }
    synchronized (LOAD_LOCK) {
      isSettingsLoaded = true;
      LOAD_LOCK.notifyAll();
    }
  }

  public static Settings getInstance() throws InterruptedException {
    if (!isSettingsLoaded) {
      synchronized (LOAD_LOCK) {
        if (!isSettingsLoaded) LOAD_LOCK.wait();
      }
    }
    return INSTANCE;
  }

  public static boolean isIsSettingsLoaded() {
    return isSettingsLoaded;
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

  public boolean getAutoSendText(String address) {
    return autoSendText
        && (autoSendTrustedList.contains(address) || autoSendTrustedList.contains("*"));
  }

  public boolean getAutoSendFiles() {
    return autoSendFiles;
  }

  public boolean getAutoSendFiles(String address) {
    return autoSendFiles
        && (autoSendTrustedList.contains(address) || autoSendTrustedList.contains("*"));
  }

  public List<String> getAutoSendTrustedList() {
    return this.autoSendTrustedList;
  }

  public boolean getVibrate() {
    return vibrate;
  }

  public boolean getScanIPv6() {
    return scanIPv6;
  }

  public boolean getCloseIfIdle() {
    return closeIfIdle;
  }

  public int getAutoCloseDelay() {
    return autoCloseDelay;
  }

  public boolean getSaveServers() {
    return saveServers;
  }

  public List<String> getSavedServersList() {
    return this.savedServersList;
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

  public void setScanIPv6(boolean scanIPv6) {
    this.scanIPv6 = scanIPv6;
  }

  public void setCloseIfIdle(boolean closeIfIdle) {
    this.closeIfIdle = closeIfIdle;
  }

  public void setAutoCloseDelay(int autoCloseDelay) {
    this.autoCloseDelay = autoCloseDelay;
  }

  public void setSaveServers(boolean saveServers) {
    this.saveServers = saveServers;
  }
}
