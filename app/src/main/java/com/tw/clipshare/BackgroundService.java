/*
 * MIT License
 *
 * Copyright (c) 2025 H. Thevindu J. Wijesekera
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

import android.app.*;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.tw.clipshare.platformUtils.AndroidUtils;
import com.tw.clipshare.protocol.Proto;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BackgroundService extends Service {
  private static final int GET_TEXT = 1;
  private static final int SEND_TEXT = 2;
  private static volatile boolean running = false;
  private static volatile int command;
  private static final Object LOCK = new Object();
  private static volatile String copiedText = null;
  private static final Object TEXT_LOCK = new Object();

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    try {
      Intent notificationIntent = new Intent(this, BackgroundService.class);
      PendingIntent pendingIntent =
          PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
      Context ctx = getApplicationContext();
      Random rnd = new Random();
      int id = Math.abs(rnd.nextInt(Integer.MAX_VALUE - 1)) + 1;
      Intent intentStop = new Intent(ctx, EventReceiver.class);
      intentStop.putExtra("stop", true);
      PendingIntent pendingIntentStop =
          PendingIntent.getBroadcast(ctx, 0, intentStop, PendingIntent.FLAG_IMMUTABLE);
      Intent intentGet = new Intent(ctx, EventReceiver.class);
      intentGet.putExtra("command", GET_TEXT);
      PendingIntent pendingIntentGet =
          PendingIntent.getBroadcast(ctx, GET_TEXT, intentGet, PendingIntent.FLAG_IMMUTABLE);
      Intent intentSend = new Intent(ctx, EventReceiver.class);
      intentSend.putExtra("command", SEND_TEXT);
      PendingIntent pendingIntentSend =
          PendingIntent.getBroadcast(ctx, SEND_TEXT, intentSend, PendingIntent.FLAG_IMMUTABLE);
      NotificationCompat.Builder builder =
          new NotificationCompat.Builder(ctx, FileService.CHANNEL_ID)
              .setContentIntent(pendingIntent)
              .setContentTitle(getApplicationContext().getString(R.string.app_name))
              .setSmallIcon(R.drawable.clip_share_icon_mono)
              .addAction(0, "Get", pendingIntentGet)
              .addAction(0, "Send", pendingIntentSend)
              .addAction(0, "Stop", pendingIntentStop);
      running = true;
      command = 0;
      startForeground(id, builder.build());
      (new Thread(
              () -> {
                try {
                  processCommands();
                } catch (Exception ignored) {
                }
              }))
          .start();
    } catch (Exception ignored) {
    }
    return START_REDELIVER_INTENT;
  }

  private void processCommands() throws InterruptedException {
    while (running) {
      synchronized (LOCK) {
        LOCK.wait();
      }
      if (command == GET_TEXT) getText();
      else if (command == SEND_TEXT) sendText();
    }
    stopForeground(STOP_FOREGROUND_REMOVE);
  }

  private void getText() {
    if (ClipShareActivity.lastAddress == null) return;
    ExecutorService executorService = Executors.newSingleThreadExecutor();
    Runnable runnable =
        () -> {
          try {
            AndroidUtils utils = new AndroidUtils(getApplicationContext(), null);
            Proto proto = Utils.getProtoWrapper(ClipShareActivity.lastAddress, utils);
            if (proto == null) {
              utils.showToast("Couldn't connect");
              return;
            }
            boolean status = proto.getText();
            proto.close();
            String text = null;
            if (status) text = proto.dataContainer.getString();
            if (text == null) {
              utils.showToast("Couldn't get text");
              return;
            }
            synchronized (TEXT_LOCK) {
              copiedText = text;
              command = GET_TEXT;
            }
            Intent intent = new Intent(this, InvisibleActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            utils.vibrate();
          } catch (Exception ignored) {
          }
        };
    executorService.submit(runnable);
  }

  private void sendText() {
    if (ClipShareActivity.lastAddress == null) return;
    ExecutorService executorService = Executors.newSingleThreadExecutor();
    Runnable sendClip =
        () -> {
          try {
            String text;
            synchronized (TEXT_LOCK) {
              if (copiedText == null) TEXT_LOCK.wait(5000);
              text = copiedText;
              copiedText = null;
              command = 0;
            }
            if (text == null) return;
            AndroidUtils utils = new AndroidUtils(getApplicationContext(), null);
            Proto proto = Utils.getProtoWrapper(ClipShareActivity.lastAddress, utils);
            if (proto == null) {
              utils.showToast("Couldn't connect");
              return;
            }
            proto.sendText(text);
            proto.close();
            utils.vibrate();
          } catch (Exception ignored) {
          }
        };
    executorService.submit(sendClip);
    try {
      Intent intent = new Intent(this, InvisibleActivity.class);
      intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(intent);
    } catch (Exception ignored) {
    }
  }

  public static void doUIOperation(AndroidUtils utils) {
    if (command == GET_TEXT) {
      String text;
      synchronized (TEXT_LOCK) {
        text = copiedText;
        command = 0;
        copiedText = null;
      }
      utils.setClipboardText(text);
    } else if (command == SEND_TEXT) {
      String text = utils.getClipboardText();
      (new Thread(
              () -> {
                synchronized (TEXT_LOCK) {
                  copiedText = text;
                  TEXT_LOCK.notifyAll();
                }
              }))
          .start();
    }
  }

  public static class EventReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent receivedIntent) {
      try {
        int cmd = 0;
        if (receivedIntent.getBooleanExtra("stop", false)) running = false;
        else cmd = receivedIntent.getIntExtra("command", 0);
        synchronized (LOCK) {
          command = cmd;
          LOCK.notifyAll();
        }
      } catch (Exception ignored) {
      }
    }
  }
}
