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
