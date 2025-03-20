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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.app.NotificationManager;
import android.content.Context;
import androidx.core.app.NotificationCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.tw.clipshare.FileService;
import java.util.Random;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class StatusNotifierTest {
  private static Context context;
  private static long[] curSizes;
  private static long[] curTimes;
  private static long[] speeds;
  private static final String SEC = TimeContainer.SECOND;
  private static final String MIN = TimeContainer.MINUTE;
  private static final String HOUR = TimeContainer.HOUR;
  private static final String DAY = TimeContainer.DAY;
  private StatusNotifier statusNotifier;

  @BeforeClass
  public static void initializeClass() {
    context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    assertNotNull(context);

    curSizes =
        new long[] {
          0,
          100000,
          200000,
          300000,
          500000,
          1000000,
          1500000,
          1700000,
          2000000,
          2100000,
          2500000,
          2600000,
          3100000,
          3300000,
          4000000,
          4500000,
          4600000,
          4700000,
          4800000,
          150000000000L,
          268470500000L,
          273411700000L,
          279670500000L,
          279885620210L,
          279983500000L,
          279996800000L,
          279998500000L,
          280000000000L
        };
    curTimes =
        new long[] {
          1000, 1120, 1280, 1440, 1600, 1880, 2040, 2200, 2240, 2320, 2520, 2600, 2880, 3000, 3440,
          3800, 3920, 4160, 4200, 100000000, 163000000, 166000000, 169800000, 169930000, 169990000,
          169998000, 169999000, 170000000
        };
    speeds =
        new long[] {
          -1, -1, -1, 681818, 681818, 909090, 909090, 909090, 909090, 1306817, 1306817, 1306817,
          1426541, 1426541, 1471691, 1471691, 1416268, 1416268, 1416268, 1437204, 1548024, 1572784,
          1591351, 1607205, 1613236, 1625552, 1644164, 1608123
        };
  }

  @Before
  public void initializeEach() {
    NotificationManager notificationManager =
        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    assertNotNull(notificationManager);
    NotificationCompat.Builder builder =
        new NotificationCompat.Builder(context, FileService.CHANNEL_ID);
    assertNotNull(builder);
    Random rnd = new Random();
    int notificationId = Math.abs(rnd.nextInt(Integer.MAX_VALUE - 1)) + 1;
    this.statusNotifier = new StatusNotifier(notificationManager, builder, notificationId);
    statusNotifier.setFileSize(curSizes[curSizes.length - 1]);
  }

  @Test
  public void getSpeedTest() {
    for (int i = 0; i < speeds.length; i++) {
      long speed = statusNotifier.getSpeed(curSizes[i], curTimes[i]);
      assertEquals(speeds[i], speed);
    }
  }

  @Test
  public void getRemainingTimeTest() {
    int[] times = {
      -1, -1, -1, 5, 5, 4, 4, 4, 4, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1, 2, 1, 3, 1, 10, 1, 0, 0
    };
    String[] units = {
      SEC, SEC, SEC, DAY, DAY, DAY, DAY, DAY, DAY, DAY, DAY, DAY, DAY, DAY, DAY, DAY, DAY, DAY, DAY,
      DAY, HOUR, HOUR, MIN, MIN, SEC, SEC, SEC, SEC
    };
    for (int i = 0; i < speeds.length; i++) {
      long speed = statusNotifier.getSpeed(curSizes[i], curTimes[i]);
      TimeContainer time = statusNotifier.getRemainingTime(curSizes[i], speed);
      assertEquals(times[i], time.time);
      assertEquals(units[i], time.unit);
    }
  }
}
