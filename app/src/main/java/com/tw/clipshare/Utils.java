package com.tw.clipshare;

public class Utils {
  public static final byte PROTOCOL_SUPPORTED = 1;
  public static final byte PROTOCOL_OBSOLETE = 2;
  public static final byte PROTOCOL_UNKNOWN = 3;

  public static boolean isValidIPv4(String str) {
    if (str == null) return false;
    return str.matches("^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)(\\.(?!$)|$)){4}$");
  }

  private Utils() {}
}
