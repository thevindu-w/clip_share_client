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
import java.net.ProtocolException;

public class ProtocolSelector {

  static final byte PROTOCOL_SUPPORTED = 1;
  static final byte PROTOCOL_OBSOLETE = 2;
  static final byte PROTOCOL_UNKNOWN = 3;
  private static final byte PROTO_MAX = 2;

  public static Proto getProto(
      ServerConnection connection, AndroidUtils utils, StatusNotifier notifier)
      throws ProtocolException {
    if (connection == null) {
      return null;
    }
    byte[] proto_v = {PROTO_MAX};
    if (!connection.send(proto_v)) {
      return null;
    }
    if (connection.receive(proto_v)) {
      return null;
    }
    if (proto_v[0] == ProtocolSelector.PROTOCOL_OBSOLETE) {
      throw new ProtocolException("Obsolete protocol");
    } else if (proto_v[0] == ProtocolSelector.PROTOCOL_UNKNOWN) {
      byte[] serverProto = new byte[1];
      if (connection.receive(serverProto)) {
        return null;
      }
      byte serverMaxProto = serverProto[0];
      if (serverMaxProto == 1) {
        proto_v[0] = serverMaxProto;
        if (!connection.send(proto_v)) {
          return null;
        }
        return new Proto_v1(connection, utils, notifier);
      }
      serverProto[0] = 0;
      connection.send(serverProto);
      throw new ProtocolException("Unknown protocol");
    } else if (proto_v[0] != ProtocolSelector.PROTOCOL_SUPPORTED) {
      return null;
    }
    return new Proto_v2(connection, utils, notifier);
  }
}
