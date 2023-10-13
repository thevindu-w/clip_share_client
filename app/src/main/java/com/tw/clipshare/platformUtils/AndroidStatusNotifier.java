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

package com.tw.clipshare.platformUtils;

import android.annotation.SuppressLint;
import android.app.Activity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class AndroidStatusNotifier implements StatusNotifier {

  private static final int PROGRESS_MAX = 100;
  private final NotificationManagerCompat notificationManager;
  private final Activity activity;
  private final NotificationCompat.Builder builder;
  private final int notificationId;
  private int prev;
  private long prevTime;

  public AndroidStatusNotifier(
      Activity activity,
      NotificationManagerCompat notificationManager,
      NotificationCompat.Builder builder,
      int notificationId) {
    this.activity = activity;
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
  }

  @Override
  public void setName(String name) {
    if (this.builder == null) return;
    try {
      this.builder.setContentTitle(name);
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
      NotificationCompat.Builder finalBuilder = builder;
      NotificationManagerCompat finalNotificationManager = notificationManager;
      builder.setSilent(true);
      this.activity.runOnUiThread(
          () -> {
            try {
              finalBuilder.setProgress(PROGRESS_MAX, value, false).setContentText(value + "%");
              finalNotificationManager.notify(notificationId, finalBuilder.build());
            } catch (Exception ignored) {
            }
          });
    } catch (Exception ignored) {
    }
  }

  @Override
  public void reset() {
    this.prev = -1;
    this.prevTime = 0;
  }

  @Override
  public void finish() {
    try {
      if (this.notificationManager != null) {
        NotificationManagerCompat finalNotificationManager = this.notificationManager;
        this.activity.runOnUiThread(
            () -> {
              try {
                finalNotificationManager.cancel(this.notificationId);
              } catch (Exception ignored) {
              }
            });
      }
    } catch (Exception ignored) {
    }
  }
}
