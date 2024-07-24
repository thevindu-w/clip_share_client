package com.tw.clipshare.platformUtils;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.*;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;
import com.tw.clipshare.ClipShareActivity;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AndroidUtilsTest {
  @Rule
  public GrantPermissionRule mRuntimePermissionRule =
      GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE);

  private Activity activity;

  @Before
  public void initializeActivity() throws InterruptedException {
    Object lock = new Object();
    try (ActivityScenario<ClipShareActivity> scenario =
        ActivityScenario.launch(ClipShareActivity.class)) {
      scenario.onActivity(
          activity -> {
            synchronized (lock) {
              AndroidUtilsTest.this.activity = activity;
              lock.notifyAll();
            }
          });
    }
    synchronized (lock) {
      if (this.activity == null) lock.wait(5000);
    }
    assertNotNull(this.activity);
  }

  @Test
  public void testClipboardMethods() {
    String text = "Sample text for clipboard test testClipboardMethods";
    try {
      Context appContext = getInstrumentation().getTargetContext();
      AndroidUtils androidUtils = new AndroidUtils(appContext, activity);

      androidUtils.setClipboardText(text);

      String received = androidUtils.getClipboardText();
      assertEquals(text, received);
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
