package com.tw.clipshare;

import java.net.Inet6Address;

public class Utils {
  public static final byte PROTOCOL_SUPPORTED = 1;
  public static final byte PROTOCOL_OBSOLETE = 2;
  public static final byte PROTOCOL_UNKNOWN = 3;

  public static boolean isValidIP(String str) {
    try {
      if (str == null) return false;
      if (str.matches("^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)(\\.(?!$)|$)){4}$")) return true;
      if (!str.contains(":")) return false;
      //noinspection ResultOfMethodCallIgnored
      Inet6Address.getByName(str);
      return true;
    } catch (Exception ignored) {
    }
    return false;
  }

  private Utils() {}
}
