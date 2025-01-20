package com.tw.clipshare;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class UtilsTest {
  @Test
  public void isValidIPTest() {
    String[] validIPv4s = {"192.168.1.1", "0.0.0.0"};
    for (String ip : validIPv4s) {
      assertTrue(Utils.isValidIP(ip));
    }
    String[] validIPv6s = {"::", "fc00:abcd::123", "fe80::1", "::1"};
    for (String ip : validIPv6s) {
      assertTrue(Utils.isValidIP(ip));
    }
    String[] invalidIPv4s = {"192.168.1.1.1", "127.0.0.256"};
    for (String ip : invalidIPv4s) {
      assertFalse(Utils.isValidIP(ip));
    }
    String[] invalidIPv6s = {":::", "fc00", "fe80:", ":1", "fe80:abcg::1", " fe80::1"};
    for (String ip : invalidIPv6s) {
      assertFalse(Utils.isValidIP(ip));
    }
  }
}
