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

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.tw.clipshare.ClipShareActivity;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AndroidUtilsTest {
  @Test
  public void testClipboardMethods() {
    String text = "Sample text for clipboard test testClipboardMethods";
    try {
      ActivityScenario.launch(ClipShareActivity.class)
          .onActivity(
              activity -> {
                Context appContext = activity.getApplicationContext();
                AndroidUtils androidUtils = new AndroidUtils(appContext, activity);

                androidUtils.setClipboardText(text);

                String received = androidUtils.getClipboardText();
                assertEquals(text, received);
              })
          .close();
    } catch (Exception ignored) {
    }
  }

  @Test
  public void testClipboardMethodsNoActivity() {
    try {
      Context appContext = getInstrumentation().getTargetContext();
      AndroidUtils androidUtils = new AndroidUtils(appContext, null);

      String received = androidUtils.getClipboardText();
      assertNull(received);
    } catch (Exception ignored) {
    }
  }
}
