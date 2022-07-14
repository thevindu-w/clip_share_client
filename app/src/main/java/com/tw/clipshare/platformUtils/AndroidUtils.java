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

    public String getClipboardText() {
        try {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
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
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("clip_share", text);
            clipboard.setPrimaryClip(clip);
        } catch (Exception ignored) {
        }
    }
}
