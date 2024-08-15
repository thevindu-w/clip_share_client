package com.tw.clipshare;

import com.tw.clipshare.platformUtils.AndroidUtils;
import com.tw.clipshare.protocol.Proto;

public class PendingTask {
  public static final int GET_FILES = 3;
  public static final int SEND_FILES = 4;
  public final Proto proto;
  public final AndroidUtils utils;
  public final int task;

  public PendingTask(Proto proto, AndroidUtils utils, int task) {
    this.proto = proto;
    this.utils = utils;
    this.task = task;
  }
}
