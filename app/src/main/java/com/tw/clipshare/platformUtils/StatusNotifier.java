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

package com.tw.clipshare.platformUtils;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

public final class StatusNotifier {

  private static final int PROGRESS_MAX = 100;
  private final NotificationManager notificationManager;
  private final NotificationCompat.Builder builder;
  private final int notificationId;
  private long fileSize;
  private long prevNotifyTime;
  private int prevProgress;
  private long prevSize;
  private long prevSpeed;
  private TimeContainer prevTimeRemaining;
  private long prevTime;
  private boolean finished;

  public StatusNotifier(
      NotificationManager notificationManager,
      NotificationCompat.Builder builder,
      int notificationId) {
    this.notificationManager = notificationManager;
    this.builder =
        builder
            .setContentText("0%")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(true)
            .setVibrate(new long[] {0L})
            .setSilent(true);
    this.notificationId = notificationId;
    this.fileSize = -1;
    this.prevNotifyTime = 0;
    this.prevProgress = -1;
    this.prevTime = 0;
    this.prevSize = -1;
    this.prevSpeed = -1;
    this.prevTimeRemaining = null;
    this.finished = false;
  }

  public void setTitle(String title) {
    if (this.builder == null) return;
    try {
      int len = title.length();
      if (len > 45) {
        title = title.substring(0, 30) + "..." + title.substring(len - 12);
      }
      this.builder.setContentTitle(title);
    } catch (Exception ignored) {
    }
  }

  public void setIcon(int icon) {
    if (this.builder == null) return;
    try {
      this.builder.setSmallIcon(icon);
    } catch (Exception ignored) {
    }
  }

  long getSpeed(long curSize, long curTime) {
    if (prevSize < 0) {
      prevSize = curSize;
      prevTime = curTime;
      return -1;
    }
    long dur = curTime - prevTime;
    if (dur >= 100) { // smaller durations cause less precision
      long speed = ((curSize - prevSize) * 1000) / dur; // Bytes per second
      if (prevSpeed > 0)
        speed = (speed + 2 * prevSpeed) / 3; // prevent too large fluctuations in speed value
      prevSpeed = speed;
      prevSize = curSize;
      prevTime = curTime;
    }
    return prevSpeed;
  }

  TimeContainer getRemainingTime(long curSize, long speed) {
    long remSize = fileSize - curSize;
    long remSeconds;
    if (speed >= 100) { // smaller values cause less precision
      remSeconds = remSize / speed;
    } else {
      remSeconds = -1;
    }
    return TimeContainer.initBySeconds(remSeconds);
  }

  @SuppressLint("MissingPermission")
  public void setProgress(long current) {
    try {
      long curTime = System.currentTimeMillis();
      long speed = getSpeed(current, curTime);
      if (curTime < this.prevNotifyTime + 500) return;
      int progress = (int) ((current * 100) / fileSize);
      TimeContainer timeRemaining = getRemainingTime(current, speed);
      if (progress == prevProgress && timeRemaining.equals(prevTimeRemaining)) return;
      this.prevProgress = progress;
      this.prevTimeRemaining = timeRemaining;
      this.prevNotifyTime = curTime;
      builder.setProgress(PROGRESS_MAX, progress, false).setContentText(progress + "%");
      if (timeRemaining.time >= 0) builder.setSubText(timeRemaining.toString());
      notificationManager.notify(notificationId, builder.build());
    } catch (Exception ignored) {
    }
  }

  public void setFileSize(long fileSize) {
    this.fileSize = fileSize;
  }

  public void reset() {
    this.prevNotifyTime = 0;
    this.prevProgress = -1;
    this.prevTime = 0;
    this.prevSize = -1;
    this.prevSpeed = -1;
    this.prevTimeRemaining = null;
  }

  public Notification getNotification() {
    return builder.build();
  }

  public int getId() {
    return this.notificationId;
  }

  public void finish() {
    synchronized (this) {
      if (this.finished) return;
      this.finished = true;
    }
    try {
      if (this.notificationManager != null) {
        this.notificationManager.cancel(this.notificationId);
      }
    } catch (Exception ignored) {
    }
  }

  @Override
  protected void finalize() throws Throwable {
    this.finish();
    super.finalize();
  }
}

class TimeContainer {
  static final String SECOND = "sec";
  static final String MINUTE = "min";
  static final String HOUR = "hour";
  static final String DAY = "day";
  final short time;
  final String unit;

  private TimeContainer(short time, String unit) {
    this.time = time;
    this.unit = unit;
  }

  static TimeContainer initBySeconds(long seconds) {
    if (seconds < 0) { // Undefined time
      return new TimeContainer((short) -1, TimeContainer.SECOND);
    }
    if (seconds < 60) {
      return new TimeContainer((short) seconds, TimeContainer.SECOND);
    }
    if (seconds < 3600) {
      return new TimeContainer((short) ((seconds + 30) / 60), TimeContainer.MINUTE);
    }
    if (seconds < 86400) {
      return new TimeContainer((short) ((seconds + 1800) / 3600), TimeContainer.HOUR);
    }
    if (seconds < 5184000) {
      return new TimeContainer((short) ((seconds + 43200) / 86400), TimeContainer.DAY);
    }
    return new TimeContainer((short) -1, TimeContainer.SECOND);
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof TimeContainer)) return false;
    TimeContainer otherContainer = (TimeContainer) other;
    return (this.time == otherContainer.time
        && (this.time < 0 || this.unit.equals(otherContainer.unit)));
  }

  @Override
  @NonNull
  public String toString() {
    if (this.time == 1) {
      return this.time + " " + this.unit;
    }
    return this.time + " " + this.unit + 's';
  }
}
