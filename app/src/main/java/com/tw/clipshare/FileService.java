package com.tw.clipshare;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.tw.clipshare.platformUtils.AndroidStatusNotifier;
import com.tw.clipshare.protocol.Proto;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class FileService extends Service {
  private static final String CHANNEL_ID = "notification_channel";
  private static LinkedList<PendingTask> pendingTasks = null;
  private ExecutorService executorService;
  private AndroidStatusNotifier statusNotifier;

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (FileService.pendingTasks == null || FileService.pendingTasks.isEmpty()) {
      endService();
      return START_NOT_STICKY;
    }
    this.statusNotifier = createStatusNotifier();
    try {
      startForeground(statusNotifier.getId(), statusNotifier.getNotification());
    } catch (Exception ignored) {
    }

    Runnable runnable = new FileShareRunnable();
    executorService = Executors.newSingleThreadExecutor();
    executorService.submit(runnable);

    // Stop service when executorService completes
    (new Thread() {
          @Override
          public void run() {
            try {
              executorService.shutdown();
              while (true) {
                try {
                  if (executorService.awaitTermination(1, TimeUnit.HOURS)) break;
                } catch (Exception ignored) {
                }
              }
              endService();
            } catch (Exception ignored) {
            }
          }
        })
        .start();

    return START_REDELIVER_INTENT;
  }

  public static void addPendingTask(PendingTask pendingTask) {
    synchronized (FileService.class) {
      if (FileService.pendingTasks == null) FileService.pendingTasks = new LinkedList<>();
    }
    //noinspection SynchronizeOnNonFinalField
    synchronized (pendingTasks) {
      pendingTasks.add(pendingTask);
    }
  }

  private void endService() {
    try {
      if (executorService != null) {
        executorService.shutdownNow();
        executorService = null;
      }
      if (FileService.pendingTasks != null) {
        // noinspection SynchronizeOnNonFinalField
        synchronized (FileService.pendingTasks) {
          FileService.pendingTasks.clear();
        }
      }
      if (this.statusNotifier != null) this.statusNotifier.finish();
    } catch (Exception ignored) {
    }
    stopForeground(STOP_FOREGROUND_REMOVE);
    stopSelf();
  }

  private AndroidStatusNotifier createStatusNotifier() {
    Intent notificationIntent = new Intent(this, FileService.class);
    PendingIntent pendingIntent =
        PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
    Context context = getApplicationContext();
    NotificationManager notificationManager =
        (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
    NotificationCompat.Builder builder =
        new NotificationCompat.Builder(context, FileService.CHANNEL_ID)
            .setContentIntent(pendingIntent);
    Random rnd = new Random();
    int notificationId = Math.abs(rnd.nextInt(Integer.MAX_VALUE - 1)) + 1;
    return new AndroidStatusNotifier(notificationManager, builder, notificationId);
  }

  private class FileShareRunnable implements Runnable {
    @Override
    public void run() {
      PendingTask pendingTask;
      while (true) {
        //noinspection SynchronizeOnNonFinalField
        synchronized (FileService.pendingTasks) {
          if (FileService.pendingTasks.isEmpty()) {
            return;
          }
          pendingTask = pendingTasks.pop();
        }

        try {
          Proto proto = pendingTask.proto;
          proto.setStatusNotifier(statusNotifier);
          switch (pendingTask.task) {
            case PendingTask.GET_FILES:
              {
                statusNotifier.reset();
                statusNotifier.setTitle("Getting file");
                statusNotifier.setIcon(R.drawable.ic_download_icon);
                this.getFiles(proto);
              }
          }
          proto.close();
        } catch (Exception ignored) {
        }
      }
    }

    private void getFiles(Proto proto) {
      try {
        proto.getFile();
      } catch (Exception ignored) {
      }
    }
  }
}
