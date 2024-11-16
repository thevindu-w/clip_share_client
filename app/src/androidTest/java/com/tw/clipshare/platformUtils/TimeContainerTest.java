package com.tw.clipshare.platformUtils;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TimeContainerTest {
  @Test
  public void toStringTest() {
    TimeContainer time;
    time = TimeContainer.initBySeconds(200000);
    assertEquals("2 days", time.toString());
    time = TimeContainer.initBySeconds(100000);
    assertEquals("1 day", time.toString());
    time = TimeContainer.initBySeconds(20000);
    assertEquals("6 hours", time.toString());
    time = TimeContainer.initBySeconds(4000);
    assertEquals("1 hour", time.toString());
    time = TimeContainer.initBySeconds(400);
    assertEquals("7 mins", time.toString());
    time = TimeContainer.initBySeconds(80);
    assertEquals("1 min", time.toString());
    time = TimeContainer.initBySeconds(10);
    assertEquals("10 secs", time.toString());
    time = TimeContainer.initBySeconds(1);
    assertEquals("1 sec", time.toString());
    time = TimeContainer.initBySeconds(0);
    assertEquals("0 secs", time.toString());
  }

  @Test
  public void equalsTest() {
    assertEquals(TimeContainer.initBySeconds(-1), TimeContainer.initBySeconds(-1));
    assertEquals(TimeContainer.initBySeconds(9999999999999L), TimeContainer.initBySeconds(-1));
    assertNotEquals(TimeContainer.initBySeconds(0), TimeContainer.initBySeconds(-1));
    assertEquals(TimeContainer.initBySeconds(0), TimeContainer.initBySeconds(0));
    assertNotEquals(TimeContainer.initBySeconds(0), TimeContainer.initBySeconds(1));
    assertEquals(TimeContainer.initBySeconds(1), TimeContainer.initBySeconds(1));
    assertEquals(TimeContainer.initBySeconds(2), TimeContainer.initBySeconds(2));
    assertNotEquals(TimeContainer.initBySeconds(60), TimeContainer.initBySeconds(59));
    assertEquals(TimeContainer.initBySeconds(60), TimeContainer.initBySeconds(60));
    assertEquals(TimeContainer.initBySeconds(60), TimeContainer.initBySeconds(61));
    assertEquals(TimeContainer.initBySeconds(60), TimeContainer.initBySeconds(89));
    assertNotEquals(TimeContainer.initBySeconds(60), TimeContainer.initBySeconds(90));
    assertEquals(TimeContainer.initBySeconds(120), TimeContainer.initBySeconds(90));
    assertEquals(TimeContainer.initBySeconds(120), TimeContainer.initBySeconds(149));
    assertEquals(TimeContainer.initBySeconds(3570), TimeContainer.initBySeconds(3599));
    assertNotEquals(TimeContainer.initBySeconds(3600), TimeContainer.initBySeconds(3599));
    assertEquals(TimeContainer.initBySeconds(3600), TimeContainer.initBySeconds(3600));
    assertEquals(TimeContainer.initBySeconds(3600), TimeContainer.initBySeconds(4000));
    assertNotEquals(TimeContainer.initBySeconds(3600), TimeContainer.initBySeconds(6000));
    assertNotEquals(TimeContainer.initBySeconds(86400), TimeContainer.initBySeconds(86399));
    assertEquals(TimeContainer.initBySeconds(86400), TimeContainer.initBySeconds(86400));
    assertEquals(TimeContainer.initBySeconds(86400), TimeContainer.initBySeconds(100000));
    assertNotEquals(TimeContainer.initBySeconds(86400), TimeContainer.initBySeconds(200000));
    assertEquals(TimeContainer.initBySeconds(172800), TimeContainer.initBySeconds(200000));
    assertNotEquals(TimeContainer.initBySeconds(-1), null);
    assertNotEquals(TimeContainer.initBySeconds(1), TimeContainer.initBySeconds(60));
  }
}
