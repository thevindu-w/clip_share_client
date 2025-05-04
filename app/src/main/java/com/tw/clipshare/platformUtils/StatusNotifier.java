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

package com.tw.clipshare.platformUtils;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import java.util.Locale;

public final class StatusNotifier {

  private static final int PROGRESS_MAX = 100;
  private final NotificationManager notificationManager;
  private final NotificationCompat.Builder builder;
  private final int notificationId;
  private long fileSize;
  private String fileSizeStr;
  private long prevNotifyTime;
  private DataSize prevProgress;
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
    this.fileSizeStr = "";
    this.prevNotifyTime = 0;
    this.prevProgress = null;
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
      if (len > 32) {
        title = title.substring(0, 20) + "..." + title.substring(len - 9);
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

  /**
   * Get the data transfer speed in Bytes per seconds.
   *
   * @param curSize current transfer amount in Bytes
   * @param curTime current time in milliseconds since a fixed time (ex: Unix epoch)
   * @return time averaged data transfer speed in Bytes/sec
   */
  long getSpeed(long curSize, long curTime) {
    if (prevSize < 0) {
      prevSize = curSize;
      prevTime = curTime;
      return -1;
    }
    long dur = curTime - prevTime;
    if (dur >= 400) { // smaller durations cause less precision and high fluctuations
      long speed = ((curSize - prevSize) * 1000) / dur; // Bytes per second
      if (prevSpeed > 0)
        speed = (speed + 3 * prevSpeed) / 4; // prevent too large fluctuations in speed value
      prevSpeed = speed;
      prevSize = curSize;
      prevTime = curTime;
    }
    return prevSpeed;
  }

  /**
   * Get estimated time remaining to complete the data transfer.
   *
   * @param curSize current transfer amount in Bytes
   * @param speed data transfer speed in Bytes/sec
   * @return estimated remaining time
   */
  TimeContainer getRemainingTime(long curSize, long speed) {
    long remSize = fileSize - curSize;
    long remSeconds;
    if (speed >= 500) { // smaller values cause less precision
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
      if (curTime < this.prevNotifyTime + 800 || curTime % 1000 > 200) return;
      long speed = getSpeed(current, curTime);
      DataSize progress = new DataSize(current);
      TimeContainer timeRemaining = getRemainingTime(current, speed);
      if (progress.equals(prevProgress) && timeRemaining.equals(prevTimeRemaining)) return;
      this.prevProgress = progress;
      this.prevTimeRemaining = timeRemaining;
      this.prevNotifyTime = curTime;
      int percent = (int) ((current * 100) / fileSize);
      builder
          .setProgress(PROGRESS_MAX, percent, false)
          .setContentText(progress + "/" + fileSizeStr);
      if (timeRemaining.time >= 0) builder.setSubText(timeRemaining + " left");
      notificationManager.notify(notificationId, builder.build());
    } catch (Exception ignored) {
    }
  }

  public void setFileSize(long fileSize) {
    this.fileSize = fileSize;
    this.fileSizeStr = (new DataSize(fileSize)).toString();
  }

  public void reset() {
    this.prevNotifyTime = 0;
    this.prevProgress = null;
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

enum DataUnit {
  B,
  KB,
  MB,
  GB,
  TB
}

class DataSize {
  final DataUnit unit;
  final float value;

  DataSize(long size) {
    int p1000;
    long size1 = size;
    for (p1000 = 0; size1 >= 1000; size1 /= 1000) {
      p1000++;
      size = size1;
    }
    if (size < 1000) this.value = (float) size;
    else this.value = size / 1000.f;
    switch (p1000) {
      case 0:
        {
          this.unit = DataUnit.B;
          break;
        }
      case 1:
        {
          this.unit = DataUnit.KB;
          break;
        }
      case 2:
        {
          this.unit = DataUnit.MB;
          break;
        }
      case 3:
        {
          this.unit = DataUnit.GB;
          break;
        }
      default:
        {
          this.unit = DataUnit.TB;
        }
    }
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof DataSize otherSize)) return false;
    if (this.unit != otherSize.unit) return false;
    return Math.round(this.value * 100) == Math.round(otherSize.value * 100);
  }

  @Override
  @NonNull
  public String toString() {
    return String.format(Locale.ENGLISH, "%.3G %s", this.value, this.unit.name());
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
    if (!(other instanceof TimeContainer otherContainer)) return false;
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
