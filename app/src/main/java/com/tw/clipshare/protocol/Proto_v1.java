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

package com.tw.clipshare.protocol;

import com.tw.clipshare.netConnection.ServerConnection;
import com.tw.clipshare.platformUtils.AndroidUtils;
import com.tw.clipshare.platformUtils.StatusNotifier;

public class Proto_v1 extends Proto {

  Proto_v1(ServerConnection serverConnection, AndroidUtils utils, StatusNotifier notifier) {
    super(serverConnection, utils, notifier);
  }

  @Override
  public String getText() {
    return this.protoMethods.v1_getText();
  }

  @Override
  public boolean sendText(String text) {
    return this.protoMethods.v1_sendText(text);
  }

  @Override
  public boolean getFile() {
    return this.protoMethods.v1_getFile();
  }

  @Override
  public boolean sendFile() {
    return this.protoMethods.v1_sendFile();
  }

  @Override
  public boolean getImage() {
    return this.protoMethods.v1_getImage();
  }

  @Override
  public String checkInfo() {
    return this.protoMethods.v1_checkInfo();
  }
}
