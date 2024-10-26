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
import androidx.core.app.NotificationCompat;

public final class AndroidStatusNotifier implements StatusNotifier {

  private static final int PROGRESS_MAX = 100;
  private final NotificationManager notificationManager;
  private final NotificationCompat.Builder builder;
  private final int notificationId;
  private int prev;
  private long prevTime;
  private boolean finished;

  public AndroidStatusNotifier(
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
    this.prev = -1;
    this.prevTime = 0;
    this.finished = false;
  }

  @Override
  public void setTitle(String title) {
    if (this.builder == null) return;
    try {
      int len = title.length();
      if (len > 45) {
        title = title.substring(0,30) + "..." + title.substring(len-12);
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

  @SuppressLint("MissingPermission")
  @Override
  public void setStatus(int value) {
    try {
      long curTime = System.currentTimeMillis();
      if (value <= this.prev || curTime < this.prevTime + 500) return;
      this.prev = value;
      this.prevTime = curTime;
      builder.setProgress(PROGRESS_MAX, value, false).setContentText(value + "%");
      notificationManager.notify(notificationId, builder.build());
    } catch (Exception ignored) {
    }
  }

  @Override
  public void reset() {
    this.prev = -1;
    this.prevTime = 0;
  }

  public Notification getNotification() {
    return builder.build();
  }

  public int getId() {
    return this.notificationId;
  }

  @Override
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
