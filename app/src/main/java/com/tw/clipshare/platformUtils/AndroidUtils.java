package com.tw.clipshare.platformUtils;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

import static android.content.ClipDescription.MIMETYPE_TEXT_HTML;
import static android.content.ClipDescription.MIMETYPE_TEXT_PLAIN;

public class AndroidUtils {

    protected final Context context;
    protected final Activity activity;

    public AndroidUtils(Context context, Activity activity) {
        this.context = context;
        this.activity = activity;
    }

    private ClipboardManager getClipboardManager() {
        try {
            Object lock = new Object();
            ClipboardManager[] clipboardManagers = new ClipboardManager[1];
            this.activity.runOnUiThread(() -> {
                clipboardManagers[0] = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                synchronized (lock) {
                    lock.notifyAll();
                }
            });
            while (clipboardManagers[0] == null) {
                try {
                    synchronized (lock) {
                        if (clipboardManagers[0] == null) {
                            lock.wait(100);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            return clipboardManagers[0];
        } catch (Exception ignored) {
            return null;
        }
    }

    public String getClipboardText() {
        try {
            ClipboardManager clipboard = this.getClipboardManager();
            if (clipboard == null || !(clipboard.hasPrimaryClip()) || !(clipboard.getPrimaryClipDescription().hasMimeType(MIMETYPE_TEXT_PLAIN) || clipboard.getPrimaryClipDescription().hasMimeType(MIMETYPE_TEXT_HTML))) {
                return null;
            }
            ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
            CharSequence clipDataSequence = item.getText();
            if (clipDataSequence == null) {
                return null;
            }
            return clipDataSequence.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    public void setClipboardText(String text) {
        try {
            ClipboardManager clipboard = this.getClipboardManager();
            ClipData clip = ClipData.newPlainText("clip_share", text);
            if (clipboard != null) clipboard.setPrimaryClip(clip);
        } catch (Exception ignored) {
        }
    }
}
