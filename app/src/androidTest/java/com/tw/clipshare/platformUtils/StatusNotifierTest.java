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
          10000,
          25000,
          35000,
          40000,
          48000,
          52000,
          55000,
          60000,
          72000,
          80000,
          88000,
          105000,
          114000,
          145000,
          167000,
          178000,
          200000,
          300000,
          35000000000L,
          69566000000L,
          69993000000L,
          69994000000L,
          69995000000L,
          69997000000L,
          69998000000L,
          69999000000L,
          70000000000L
        };
    curTimes =
        new long[] {
          1000, 1030, 1070, 1110, 1150, 1220, 1260, 1300, 1310, 1330, 1380, 1400, 1470, 1500, 1610,
          1700, 1730, 1790, 1800, 9160000, 40960000, 41262000, 41263000, 41264000, 41265000,
          41266000, 41267000, 41268000
        };
    speeds =
        new long[] {
          -1, -1, -1, 318181, 318181, 251514, 251514, 251514, 251514, 240403, 240403, 240403,
          238840, 238840, 254464, 254464, 261309, 261309, 261309, 1448093, 1327722, 1356450,
          1237633, 1158422, 1438948, 1292632, 1195088, 1130058
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
    statusNotifier.setFileSize(70000000000L);
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
      -1, -1, -1, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 7, 5, 5, 4, 4, 2, 1, 0, 0
    };
    String[] units = {
      SEC, SEC, SEC, DAY, DAY, DAY, DAY, DAY, DAY, DAY, DAY, DAY, DAY, DAY, DAY, DAY, DAY, DAY, DAY,
      HOUR, MIN, SEC, SEC, SEC, SEC, SEC, SEC, SEC
    };
    for (int i = 0; i < speeds.length; i++) {
      long speed = statusNotifier.getSpeed(curSizes[i], curTimes[i]);
      TimeContainer time = statusNotifier.getRemainingTime(curSizes[i], speed);
      assertEquals(times[i], time.time);
      assertEquals(units[i], time.unit);
    }
  }
}
