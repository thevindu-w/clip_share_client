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

import static android.content.ClipDescription.MIMETYPE_TEXT_HTML;
import static android.content.ClipDescription.MIMETYPE_TEXT_PLAIN;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

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
              || clipboard.getPrimaryClipDescription().hasMimeType(MIMETYPE_TEXT_HTML))) {
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
}
