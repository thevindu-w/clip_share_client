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

import static android.content.ClipDescription.MIMETYPE_TEXT_HTML;
import static android.content.ClipDescription.MIMETYPE_TEXT_PLAIN;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.*;
import android.widget.Toast;
import com.tw.clipshare.Settings;

public class AndroidUtils {
  private static long lastToastTime = 0;

  protected final Context context;
  private final Activity activity;

  public AndroidUtils(Context context, Activity activity) {
    this.context = context;
    this.activity = activity;
  }

  private ClipboardManager getClipboardManager() {
    try {
      Object lock = new Object();
      ClipboardManager[] clipboardManagers = new ClipboardManager[1];
      this.activity.runOnUiThread(
          () -> {
            clipboardManagers[0] =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
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

  /**
   * Get the text copied to the clipboard.
   *
   * @return text copied to the clipboard as a String or null on error.
   */
  public String getClipboardText() {
    try {
      ClipboardManager clipboard = this.getClipboardManager();
      if (clipboard == null
          || !(clipboard.hasPrimaryClip())
          || !(clipboard.getPrimaryClipDescription().hasMimeType(MIMETYPE_TEXT_PLAIN)
              || !clipboard.getPrimaryClipDescription().hasMimeType(MIMETYPE_TEXT_HTML))) {
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

  /**
   * Copy the text to the clipboard.
   *
   * @param text to be copied to the clipboard
   */
  public void setClipboardText(String text) {
    try {
      ClipboardManager clipboard = this.getClipboardManager();
      ClipData clip = ClipData.newPlainText("clip_share", text);
      if (clipboard != null) clipboard.setPrimaryClip(clip);
    } catch (Exception ignored) {
    }
  }

  public void showToast(String message) {
    if (this.context == null) return;
    try {
      long currTime = System.currentTimeMillis();
      if (currTime - lastToastTime < 2000) return;
      lastToastTime = currTime;
      Handler handler = new Handler(Looper.getMainLooper());
      handler.post(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
    } catch (Exception ignored) {
    }
  }

  @SuppressWarnings("deprecation")
  public void vibrate() {
    try {
      if (context == null || !Settings.getInstance().getVibrate()) return;
      Vibrator vibrator;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        VibratorManager vibratorManager =
            (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
        vibrator = vibratorManager.getDefaultVibrator();
      } else {
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
      }

      final int duration = 100;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(
            VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
      } else {
        vibrator.vibrate(duration);
      }
    } catch (Exception ignored) {
    }
  }
}
