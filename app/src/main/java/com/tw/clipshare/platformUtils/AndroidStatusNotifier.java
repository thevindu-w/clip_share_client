package com.tw.clipshare.platformUtils;

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

    public AndroidStatusNotifier(Activity activity, NotificationManagerCompat notificationManager, NotificationCompat.Builder builder, int notificationId) {
        this.activity = activity;
        this.notificationManager = notificationManager;
        this.builder = builder;
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

    @Override
    public void setStatus(int value) {
        try {
            long curTime = System.currentTimeMillis();
            if (value <= this.prev || curTime < this.prevTime + 500) return;
            this.prev = value;
            this.prevTime = curTime;
            NotificationCompat.Builder finalBuilder = builder;
            NotificationManagerCompat finalNotificationManager = notificationManager;
            this.activity.runOnUiThread(() -> {
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
                this.activity.runOnUiThread(() -> {
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
