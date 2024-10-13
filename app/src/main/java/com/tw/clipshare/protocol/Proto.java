/*
 * MIT License
 *
 * Copyright (c) 2022-2024 H. Thevindu J. Wijesekera
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

public abstract class Proto {
  protected final ProtoMethods protoMethods;

  protected Proto(ServerConnection serverConnection, AndroidUtils utils, StatusNotifier notifier) {
    this.protoMethods = new ProtoMethods(serverConnection, utils, notifier);
  }

  public void setStatusNotifier(StatusNotifier notifier) {
    this.protoMethods.setStatusNotifier(notifier);
  }

  /** Close the connection used for communicating with the server */
  public void close() {
    this.protoMethods.close();
  }

  public abstract String getText();

  public abstract boolean sendText(String text);

  public abstract boolean getFile();

  public abstract boolean sendFile();

  public abstract boolean getImage();

  public abstract String checkInfo();

  public void requestStop() {
    this.protoMethods.requestStop();
  }

  public boolean isStopped() {
    return this.protoMethods.isStopped();
  }
}
