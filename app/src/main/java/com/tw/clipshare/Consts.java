package com.tw.clipshare;

public class Consts {
  public static final byte PROTOCOL_SUPPORTED = 1;
  public static final byte PROTOCOL_OBSOLETE = 2;
  public static final byte PROTOCOL_UNKNOWN = 3;

  public static final String IPV4_REGEX = "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)(\\.(?!$)|$)){4}$";

  private Consts() {}
}
