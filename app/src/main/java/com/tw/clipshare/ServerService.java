/*
 * MIT License
 *
 * Copyright (c) 2026 H. Thevindu J. Wijesekera
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
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.tw.clipshare.netConnection.PlainConnection;
import com.tw.clipshare.netConnection.SecureConnection;
import com.tw.clipshare.netConnection.SocketConnection;
import com.tw.clipshare.platformUtils.AndroidUtils;
import com.tw.clipshare.platformUtils.FSUtils;
import java.io.IOException;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import javax.net.ssl.SSLSocket;

public class ServerService extends Service {
  private static volatile boolean running = false;
  private static final int BUF_SZ = 65536;
  private static Thread serverThread;
  private static final LinkedBlockingQueue<PendingOperation> pendingOperations =
      new LinkedBlockingQueue<>(8);

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
              .addAction(0, getString(R.string.stop), pendingIntentStop);
      try {
        Enumeration<NetworkInterface> netIFEnum = NetworkInterface.getNetworkInterfaces();
        List<NetworkInterface> netIFList = Collections.list(netIFEnum);
        List<String> addresses = new ArrayList<>(netIFList.size());
        for (NetworkInterface netIF : netIFList) {
          if (netIF == null || netIF.isLoopback() || !netIF.isUp() || netIF.isVirtual()) continue;
          List<InterfaceAddress> addrs = netIF.getInterfaceAddresses();
          for (InterfaceAddress intAddr : addrs) {
            InetAddress myAddr = intAddr.getAddress();
            if (myAddr instanceof Inet4Address) addresses.add(myAddr.getHostAddress());
          }
        }
        if (!addresses.isEmpty()) builder.setContentText(String.join(", ", addresses));
      } catch (Exception ignored) {
      }
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

  private long readSize(SocketConnection connection) throws IOException {
    byte[] buf = new byte[8];
    if (connection.receive(buf)) throw new IOException();
    long size = 0;
    for (byte b : buf) {
      size = (size << 8) | (b & 0xFF);
    }
    return size;
  }

  private void sendSize(SocketConnection connection, long size) throws IOException {
    byte[] buf = new byte[8];
    for (int i = 7; size > 0; i--) {
      buf[i] = (byte) (size & 0xFF);
      size >>= 8;
    }
    if (connection.send(buf)) throw new IOException();
  }

  private String readString(SocketConnection connection, int maxLen) throws IOException {
    long size = readSize(connection);
    if (size <= 0 || size >= maxLen) throw new IOException();
    int len = (int) size;
    byte[] buf = new byte[len];
    if (connection.receive(buf)) throw new IOException();
    return new String(buf, StandardCharsets.UTF_8);
  }

  private void sendText(SocketConnection connection, byte proto) throws IOException {
    List<String> container = new LinkedList<>();
    try {
      pendingOperations.put(
          (AndroidUtils utils) -> {
            String text = utils.getClipboardText();
            synchronized (container) {
              container.add(text);
              container.notifyAll();
            }
          });
      Intent intent = new Intent(this, InvisibleActivity.class);
      intent.setFlags(
          Intent.FLAG_ACTIVITY_CLEAR_TASK
              | Intent.FLAG_ACTIVITY_NEW_TASK
              | Intent.FLAG_ACTIVITY_NO_ANIMATION
              | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
      InvisibleActivity.setIsServer(true);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setPendingIntentBackgroundActivityStartMode(
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
        startActivity(intent, options.toBundle());
      } else {
        startActivity(intent);
      }

      String text = null;
      synchronized (container) {
        container.wait(2000);
        if (!container.isEmpty()) text = container.remove(0);
      }
      if (text == null) {
        byte[] method_ack = {2};
        connection.send(method_ack);
        return;
      }
      byte[] method_ack = {1};
      if (connection.send(method_ack)) return;
      byte[] buf = text.getBytes(StandardCharsets.UTF_8);
      sendSize(connection, buf.length);
      if (connection.send(buf)) return;
      if (proto >= 4) {
        byte[] ack = new byte[1];
        connection.receive(ack);
      }
    } catch (Exception ignored) {
    }
  }

  private void receiveText(SocketConnection connection, byte proto) throws IOException {
    byte[] method_ack = {1};
    if (connection.send(method_ack)) return;
    String text = readString(connection, 65536);
    if (proto >= 4) {
      byte[] ack = {1};
      connection.send(ack);
    }
    connection.close();

    try {
      pendingOperations.put((AndroidUtils utils) -> utils.setClipboardText(text));
      Intent intent = new Intent(this, InvisibleActivity.class);
      intent.setFlags(
          Intent.FLAG_ACTIVITY_CLEAR_TASK
              | Intent.FLAG_ACTIVITY_NEW_TASK
              | Intent.FLAG_ACTIVITY_NO_ANIMATION
              | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
      InvisibleActivity.setIsServer(true);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setPendingIntentBackgroundActivityStartMode(
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
        startActivity(intent, options.toBundle());
      } else {
        startActivity(intent);
      }
    } catch (Exception ignored) {
    }
  }

  private void receiveFiles(SocketConnection connection, byte proto) throws IOException {
    byte[] method_ack = {1};
    if (connection.send(method_ack)) return;
    long fileCnt = readSize(connection);
    if (fileCnt <= 0 || fileCnt > 4096) return;
    FSUtils utils = new FSUtils(getApplicationContext());
    for (long fileNum = 0; fileNum < fileCnt && running; fileNum++) {
      String fileName = readString(connection, 1024);
      if (fileName.isEmpty()) return;
      long fileSize = readSize(connection);
      if (fileSize < 0) {
        if (utils.createDirectory(fileName)) continue;
        return;
      } else if (fileSize > 1073741824L) return;
      OutputStream out = utils.getFileOutStream(fileName);
      if (out == null) return;
      byte[] buf = new byte[BUF_SZ];
      while (fileSize > 0 && running) {
        int readSz = (int) Math.min(fileSize, BUF_SZ);
        if (connection.receive(buf, 0, readSz)) return;
        fileSize -= readSz;
        out.write(buf, 0, readSz);
      }
      out.close();
    }
    if (proto >= 4) {
      byte[] ack = {1};
      connection.send(ack);
    }
    connection.close();
    utils.finish();
  }

  private void handleConnection(SocketConnection connection) {
    try {
      final int min_proto = 3;
      final int max_proto = 4;
      byte[] buf = new byte[1];
      if (connection.receive(buf)) return;
      byte proto = buf[0];
      if (proto < min_proto || proto > max_proto) {
        buf[0] = proto < min_proto ? PROTOCOL_OBSOLETE : PROTOCOL_UNKNOWN;
        connection.send(buf);
        if (proto > max_proto) {
          buf[0] = max_proto;
          connection.send(buf);
          if (connection.receive(buf)) return;
          proto = buf[0];
        }
      } else {
        buf[0] = PROTOCOL_SUPPORTED;
        if (connection.send(buf)) return;
      }
      if (proto < min_proto || proto > max_proto) return;

      if (connection.receive(buf)) return;
      byte method = buf[0];
      if (method == 1) sendText(connection, proto);
      else if (method == 2) receiveText(connection, proto);
      else if (method == 4) receiveFiles(connection, proto);
      else {
        buf[0] = 4;
        connection.send(buf);
      }
      connection.close();
    } catch (IOException | RuntimeException ignored) {
    }
  }

  public static void doUIOperation(AndroidUtils utils) {
    while (!pendingOperations.isEmpty()) {
      try {
        PendingOperation operation = pendingOperations.take();
        operation.execute(utils);
      } catch (Exception ignored) {
      }
    }
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
                try (DatagramSocket sndSock =
                    new DatagramSocket(settings.getServerPortUDP(), myAddr)) {
                  sndSock.setSoTimeout(2000);
                  try (DatagramSocket socket =
                      new DatagramSocket(settings.getServerPortUDP(), brd)) {
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
    ServerSocket ss = null;
    String[] acceptedServers = null;
    try {
      if (settings.getSecure()) {
        ss =
            SecureConnection.getSecureServerSocket(
                settings.getServerPortSecure(),
                settings.getCACertInputStream(),
                settings.getCertInputStream(),
                settings.getPasswd());
        acceptedServers = settings.getTrustedList().toArray(new String[0]);
      } else {
        ss = new ServerSocket(settings.getServerPort(), 3);
      }
      ss.setSoTimeout(2000);
      while (running) {
        SocketConnection connection = null;
        try {
          Socket sock = ss.accept();
          if (settings.getSecure()) {
            connection = new SecureConnection((SSLSocket) sock, acceptedServers);
          } else {
            connection = new PlainConnection(sock);
          }
        } catch (SocketTimeoutException ignored) {
        }
        if (connection == null) continue;
        handleConnection(connection);
        connection.close();
      }
    } finally {
      if (ss != null) ss.close();
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

  private interface PendingOperation {
    void execute(AndroidUtils utils);
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
