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

import androidx.annotation.NonNull;
import com.tw.clipshare.netConnection.PlainConnection;
import com.tw.clipshare.netConnection.SecureConnection;
import com.tw.clipshare.netConnection.ServerConnection;
import com.tw.clipshare.platformUtils.AndroidUtils;
import com.tw.clipshare.protocol.Proto;
import com.tw.clipshare.protocol.ProtocolSelector;
import java.io.InputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.ProtocolException;

public class Utils {
  public static final byte PROTOCOL_SUPPORTED = 1;
  public static final byte PROTOCOL_OBSOLETE = 2;
  public static final byte PROTOCOL_UNKNOWN = 3;

  public static boolean isValidIP(String str) {
    try {
      if (str == null) return false;
      if (str.matches("^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)(\\.(?!$)|$)){4}$")) return true;
      if (!str.contains(":")) return false;
      //noinspection ResultOfMethodCallIgnored
      Inet6Address.getByName(str);
      return true;
    } catch (Exception ignored) {
    }
    return false;
  }

  /** Opens a ServerConnection. Returns null on error. */
  private static ServerConnection getServerConnection(@NonNull String addressStr) {
    int retries = 2;
    do {
      try {
        Settings settings = Settings.getInstance();
        if (settings.getSecure()) {
          InputStream caCertIn = settings.getCACertInputStream();
          InputStream clientCertKeyIn = settings.getCertInputStream();
          char[] clientPass = settings.getPasswd();
          if (clientCertKeyIn == null || clientPass == null) {
            return null;
          }
          String[] acceptedServers = settings.getTrustedList().toArray(new String[0]);
          return new SecureConnection(
              InetAddress.getByName(addressStr),
              settings.getPortSecure(),
              caCertIn,
              clientCertKeyIn,
              clientPass,
              acceptedServers);
        } else {
          return new PlainConnection(InetAddress.getByName(addressStr), settings.getPort());
        }
      } catch (Exception ignored) {
      }
    } while (retries-- > 0);
    return null;
  }

  public static Proto getProtoWrapper(@NonNull String address, AndroidUtils utils)
      throws ProtocolException {
    int retries = 1;
    do {
      try {
        ServerConnection connection = getServerConnection(address);
        if (connection == null) continue;
        Proto proto = ProtocolSelector.getProto(connection, utils, null);
        if (proto != null) return proto;
        connection.close();
      } catch (ProtocolException ex) {
        throw ex;
      } catch (Exception ignored) {
      }
    } while (retries-- > 0);
    return null;
  }

  private Utils() {}
}
