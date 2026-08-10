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

package com.tw.clipshare;

import android.app.*;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.tw.clipshare.platformUtils.AndroidUtils;
import com.tw.clipshare.platformUtils.DataContainer;
import com.tw.clipshare.platformUtils.StatusNotifier;
import com.tw.clipshare.protocol.Proto;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class FileService extends Service {
  public static final String CHANNEL_ID = "notification_channel";
  private static LinkedList<PendingTask> pendingTasks = null;
  private ExecutorService executorService;
  private StatusNotifier statusNotifier;
  private static final Object LOCK = new Object();
  private static DataContainer data;

  static DataContainer getNextMessage() throws InterruptedException {
    DataContainer current;
    try {
      synchronized (LOCK) {
        LOCK.wait(2000);
        current = data;
      }
    } finally {
      synchronized (LOCK) {
        data = null;
      }
    }
    return current;
  }

  static void setMessage(DataContainer dataContainer, String msg) {
    synchronized (LOCK) {
      data = dataContainer != null ? dataContainer : new DataContainer();
      data.setMessage(msg);
      LOCK.notifyAll();
    }
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  private static final class RunningTasksHolder {
    static final HashMap<Integer, FileShareRunnable> runningTasks = new HashMap<>(1);
  }

  public static boolean isStopped() {
    return RunningTasksHolder.runningTasks.isEmpty();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (FileService.pendingTasks == null || FileService.pendingTasks.isEmpty()) {
      endService();
      return START_NOT_STICKY;
    }
    int id = createStatusNotifier();
    try {
      startForeground(statusNotifier.getId(), statusNotifier.getNotification());
    } catch (Exception ignored) {
    }

    LinkedList<PendingTask> pendingTasksInstance;
    // noinspection SynchronizeOnNonFinalField
    synchronized (FileService.pendingTasks) {
      pendingTasksInstance = new LinkedList<>(pendingTasks);
      FileService.pendingTasks.clear();
    }
    FileShareRunnable runnable = new FileShareRunnable(pendingTasksInstance, id);
    synchronized (RunningTasksHolder.runningTasks) {
      RunningTasksHolder.runningTasks.put(id, runnable);
    }
    executorService = Executors.newSingleThreadExecutor();
    executorService.submit(runnable);

    // Stop service when executorService completes
    (new Thread(
            () -> {
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
            }))
        .start();

    return START_REDELIVER_INTENT;
  }

  public static void addPendingTask(PendingTask pendingTask) {
    synchronized (FileService.class) {
      if (FileService.pendingTasks == null) FileService.pendingTasks = new LinkedList<>();
    }
    //noinspection SynchronizeOnNonFinalField
    synchronized (FileService.pendingTasks) {
      FileService.pendingTasks.add(pendingTask);
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

  private int createStatusNotifier() {
    Intent notificationIntent = new Intent(this, FileService.class);
    PendingIntent pendingIntent =
        PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
    Context context = getApplicationContext();
    NotificationManager notificationManager =
        (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);

    Random rnd = new Random();
    int notificationId = Math.abs(rnd.nextInt(Integer.MAX_VALUE - 1)) + 1;
    if (RunningTasksHolder.runningTasks.containsKey(notificationId))
      notificationId = Math.abs(rnd.nextInt(Integer.MAX_VALUE - 1)) + 1;
    Intent intent = new Intent(context, StopEventReceiver.class);
    intent.putExtra("TaskID", notificationId);
    PendingIntent pendingIntentStop =
        PendingIntent.getBroadcast(context, notificationId, intent, PendingIntent.FLAG_IMMUTABLE);

    NotificationCompat.Builder builder =
        new NotificationCompat.Builder(context, FileService.CHANNEL_ID)
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.drawable.clip_share_icon_mono)
            .addAction(0, getString(R.string.stop), pendingIntentStop);
    this.statusNotifier = new StatusNotifier(context, notificationManager, builder, notificationId);
    return notificationId;
  }

  private class FileShareRunnable implements Runnable {
    private final LinkedList<PendingTask> pendingTasks;
    private final int id;
    private Proto proto;

    FileShareRunnable(LinkedList<PendingTask> pendingTasks, int id) {
      this.pendingTasks = pendingTasks;
      this.proto = null;
      this.id = id;
    }

    @Override
    public void run() {
      try {
        PendingTask pendingTask;
        while (!this.pendingTasks.isEmpty()) {
          pendingTask = this.pendingTasks.pop();

          proto = pendingTask.proto();
          AndroidUtils utils = pendingTask.utils();
          try {
            proto.setStatusNotifier(statusNotifier);
            statusNotifier.reset();
            boolean success = false;
            switch (pendingTask.task()) {
              case PendingTask.GET_FILES:
                {
                  statusNotifier.setTitle(getString(R.string.getting_file));
                  statusNotifier.setIcon(R.drawable.ic_download_icon);
                  if (proto.getFile()) success = true;
                  else if (!proto.isStopped())
                    utils.showToast(getString(R.string.get_files_failed));
                  break;
                }
              case PendingTask.SEND_FILES:
                {
                  statusNotifier.setTitle(getString(R.string.sending_file));
                  statusNotifier.setIcon(R.drawable.ic_upload_icon);
                  if (proto.sendFile()) {
                    success = true;
                    utils.showToast(getString(R.string.sent_all_files));
                  } else if (!proto.isStopped())
                    utils.showToast(getString(R.string.send_files_failed));
                  break;
                }
            }
            if (proto.isStopped()) {
              setMessage(
                  null,
                  getString(
                      pendingTask.task() == PendingTask.GET_FILES
                          ? R.string.get_files_stopped
                          : R.string.send_files_stopped));
              break;
            }
            utils.vibrate();
            setMessage(
                proto.dataContainer,
                getString(
                    pendingTask.task() == PendingTask.GET_FILES
                        ? (success ? R.string.get_files_complete : R.string.get_files_failed)
                        : (success ? R.string.send_files_complete : R.string.send_files_failed)));
          } catch (Exception ignored) {
          } finally {
            proto.close();
          }
        }
      } catch (Exception ignored) {
      } finally {
        synchronized (RunningTasksHolder.runningTasks) {
          RunningTasksHolder.runningTasks.remove(this.id);
        }
      }
    }

    void requestStop() {
      proto.requestStop();
    }
  }

  public static class StopEventReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      try {
        int id = intent.getIntExtra("TaskID", -1);
        if (id == -1) return;
        FileShareRunnable runnable;
        synchronized (RunningTasksHolder.runningTasks) {
          runnable = RunningTasksHolder.runningTasks.get(id);
        }
        if (runnable == null) return;
        runnable.requestStop();
        Toast.makeText(context, R.string.cancelled, Toast.LENGTH_SHORT).show();
      } catch (Exception ignored) {
      }
    }
  }
}
