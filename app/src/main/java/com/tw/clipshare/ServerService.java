/*
 * MIT License
 *
 * Copyright (c) 2025 H. Thevindu J. Wijesekera
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

import static com.tw.clipshare.Utils.*;

import android.app.*;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.tw.clipshare.netConnection.PlainConnection;
import com.tw.clipshare.netConnection.SecureConnection;
import com.tw.clipshare.netConnection.SocketConnection;
import com.tw.clipshare.platformUtils.AndroidUtils;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLSocket;

public class ServerService extends Service {
  private static volatile boolean running = false;
  private static Thread serverThread;
  private static String receivedText;

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    try {
      Intent notificationIntent = new Intent(this, ServerService.class);
      PendingIntent pendingIntent =
          PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
      Context ctx = getApplicationContext();
      Random rnd = new Random();
      int id = Math.abs(rnd.nextInt(Integer.MAX_VALUE - 1)) + 1;
      Intent intentStop = new Intent(ctx, EventReceiver.class);
      intentStop.putExtra("stop", true);
      PendingIntent pendingIntentStop =
          PendingIntent.getBroadcast(ctx, 0, intentStop, PendingIntent.FLAG_IMMUTABLE);
      NotificationCompat.Builder builder =
          new NotificationCompat.Builder(ctx, FileService.CHANNEL_ID)
              .setContentIntent(pendingIntent)
              .setContentTitle(getApplicationContext().getString(R.string.app_name))
              .setSmallIcon(R.drawable.clip_share_icon_mono)
              .addAction(0, "Stop", pendingIntentStop);
      running = true;
      startForeground(id, builder.build());
      serverThread =
          new Thread(
              () -> {
                try {
                  startServer();
                } catch (Exception ignored) {
                }
              });
      serverThread.start();
    } catch (Exception ignored) {
    }
    return START_REDELIVER_INTENT;
  }

  private String receiveText(SocketConnection connection) {
    byte[] buf = new byte[1];
    if (connection.receive(buf)) return null;
    byte proto = buf[0];
    if (proto != 3) {
      buf[0] = proto < 3 ? PROTOCOL_OBSOLETE : PROTOCOL_UNKNOWN;
      connection.send(buf);
      if (proto > 3) {
        buf[0] = 3;
        connection.send(buf);
        if (connection.receive(buf)) return null;
        proto = buf[0];
      }
    }
    if (proto > 3) return null;
    buf[0] = PROTOCOL_SUPPORTED;
    if (connection.send(buf)) return null;

    if (connection.receive(buf)) return null;
    if (buf[0] != 2) {
      buf[0] = 4;
      connection.send(buf);
      return null;
    }
    buf[0] = 1;
    if (connection.send(buf)) return null;

    buf = new byte[8];
    if (connection.receive(buf)) return null;
    long size = 0;
    for (byte b : buf) {
      size = (size << 8) | (b & 0xFF);
    }
    if (size <= 0 || size >= 65536) return null;
    int len = (int) size;
    buf = new byte[len];
    if (connection.receive(buf)) return null;
    return new String(buf, StandardCharsets.UTF_8);
  }

  public static void doUIOperation(AndroidUtils utils) {
    if (receivedText == null) return;
    utils.setClipboardText(receivedText);
    receivedText = null;
  }

  private static ExecutorService startUDPServer() throws SocketException, InterruptedException {
    Settings settings = Settings.getInstance();
    if (!settings.getUDPServerEnabled()) return null;
    Enumeration<NetworkInterface> netIFEnum = NetworkInterface.getNetworkInterfaces();
    List<NetworkInterface> netIFList = Collections.list(netIFEnum);
    ExecutorService executorService = Executors.newCachedThreadPool();
    for (NetworkInterface netIF : netIFList) {
      if (netIF == null || netIF.isLoopback() || !netIF.isUp() || netIF.isVirtual()) continue;
      Runnable r =
          () -> {
            try {
              List<InterfaceAddress> addrs = netIF.getInterfaceAddresses();
              for (InterfaceAddress intAddr : addrs) {
                InetAddress brd = intAddr.getBroadcast();
                if (!(brd instanceof Inet4Address)) continue;
                InetAddress myAddr = intAddr.getAddress();
                if (!(myAddr instanceof Inet4Address)) continue;
                try (DatagramSocket sndSock = new DatagramSocket(settings.getPortUDP(), myAddr)) {
                  sndSock.setSoTimeout(2000);
                  try (DatagramSocket socket = new DatagramSocket(settings.getPortUDP(), brd)) {
                    socket.setSoTimeout(2000);
                    byte[] recvBuf = new byte[8];
                    byte[] sndBuf = "clip_share".getBytes(StandardCharsets.UTF_8);
                    while (running) {
                      try {
                        DatagramPacket recvPkt = new DatagramPacket(recvBuf, recvBuf.length);
                        socket.receive(recvPkt);
                        if (myAddr.equals(recvPkt.getAddress())) continue;
                        String recvMsg =
                            new String(recvPkt.getData(), StandardCharsets.UTF_8).replace("\0", "");
                        if (!"in".equals(recvMsg)) continue;
                        DatagramPacket pkt =
                            new DatagramPacket(
                                sndBuf, sndBuf.length, recvPkt.getAddress(), recvPkt.getPort());
                        sndSock.send(pkt);
                      } catch (Exception ignored) {
                      }
                    }
                  }
                }
                break;
              }
            } catch (Exception ignored) {
            }
          };
      executorService.submit(r);
    }
    return executorService;
  }

  private void startTCPServer() throws Exception {
    Settings settings = Settings.getInstance();
    ServerSocket ss;
    if (settings.getSecure()) {
      ss =
          SecureConnection.getSecureServerSocket(
              settings.getServerPortSecure(),
              settings.getCACertInputStream(),
              settings.getCertInputStream(),
              settings.getPasswd());
    } else {
      ss = new ServerSocket(settings.getServerPort(), 3);
    }
    ss.setSoTimeout(2000);
    while (running) {
      Socket sock = null;
      try {
        sock = ss.accept();
      } catch (SocketTimeoutException ignored) {
      }
      if (sock == null) continue;
      SocketConnection connection;
      if (settings.getSecure()) {
        connection = new SecureConnection((SSLSocket) sock);
      } else {
        connection = new PlainConnection(sock);
      }
      String text = receiveText(connection);
      if (text != null) {
        receivedText = text;
        try {
          Intent intent = new Intent(this, InvisibleActivity.class);
          intent.setFlags(
              Intent.FLAG_ACTIVITY_CLEAR_TASK
                  | Intent.FLAG_ACTIVITY_NEW_TASK
                  | Intent.FLAG_ACTIVITY_NO_ANIMATION
                  | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
          InvisibleActivity.setIsServer(true);
          startActivity(intent);
        } catch (Exception ignored) {
        }
      }
      connection.close();
    }
  }

  private void startServer() throws Exception {
    ExecutorService udpService = startUDPServer();
    try {
      startTCPServer();
    } catch (Exception ignored) {
    }
    if (udpService != null) udpService.shutdownNow();
    stopForeground(STOP_FOREGROUND_REMOVE);
  }

  public static class EventReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent receivedIntent) {
      try {
        if (receivedIntent.getBooleanExtra("stop", false)) running = false;
        serverThread.interrupt();
      } catch (Exception ignored) {
      }
    }
  }
}
