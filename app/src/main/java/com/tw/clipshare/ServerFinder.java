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

package com.tw.clipshare;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class ServerFinder implements Runnable {

    private static final HashMap<String, InetAddress> serverAddresses = new HashMap<>(2);
    private static ExecutorService executorStatic;
    private final NetworkInterface netIF;
    private final Thread parent;
    private final int port;
    private final int portUDP;

    private ServerFinder(NetworkInterface netIF, int port, int portUDP, Thread parent) {
        this.netIF = netIF;
        this.parent = parent;
        this.port = port;
        this.portUDP = portUDP;
    }

    public static List<InetAddress> find(int port, int portUDP) {
        try {
            synchronized (serverAddresses) {
                serverAddresses.clear();
            }
            if (executorStatic != null) executorStatic.shutdownNow();
            Enumeration<NetworkInterface> netIFEnum = NetworkInterface.getNetworkInterfaces();
            Object[] netIFList = Collections.list(netIFEnum).toArray();
            executorStatic = Executors.newFixedThreadPool(netIFList.length);
            ExecutorService executor = executorStatic;
            Thread curThread = Thread.currentThread();
            for (Object netIFList1 : netIFList) {
                NetworkInterface ni = (NetworkInterface) netIFList1;
                Runnable task = new ServerFinder(ni, port, portUDP, curThread);
                executor.submit(task);
            }
            while (!executor.isTerminated()) {
                if (!serverAddresses.isEmpty()) {
                    executor.shutdownNow();
                    break;
                }
                try {
                    //noinspection ResultOfMethodCallIgnored
                    executor.awaitTermination(500, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ignored) {
                    break;
                }
                executor.shutdown();
            }
            executor.shutdownNow();
        } catch (IOException | RuntimeException ignored) {
            if (executorStatic != null) executorStatic.shutdownNow();
        }
        List<InetAddress> addresses;
        synchronized (serverAddresses) {
            addresses = new ArrayList<>(serverAddresses.values());
        }
        return addresses;
    }

    private void scanUDP(Inet4Address broadcastAddress, Inet4Address myAddress) {
        new Thread(() -> {
            try {
                DatagramSocket socket = new DatagramSocket();
                byte[] buf = "in".getBytes();
                DatagramPacket pkt = new DatagramPacket(buf, buf.length, broadcastAddress, portUDP);
                socket.send(pkt);
                buf = new byte[256];
                pkt = new DatagramPacket(buf, buf.length);
                int timeout = 1000;
                while (true) {
                    socket.setSoTimeout(timeout);
                    timeout = 200;
                    try {
                        socket.receive(pkt);
                    } catch (SocketTimeoutException ignored) {
                        break;
                    }
                    InetAddress serverAddress = pkt.getAddress();
                    if (myAddress.equals(serverAddress)) continue;
                    String received = new String(pkt.getData()).replace("\0", "");
                    if ("clip_share".equals(received)) {
                        String addressStr = serverAddress.getHostAddress();
                        if (addressStr != null) {
                            addressStr = addressStr.intern();
                            synchronized (serverAddresses) {
                                serverAddresses.put(addressStr, serverAddress);
                            }
                        }
                    }
                }
                if (!serverAddresses.isEmpty()) parent.interrupt();
                socket.close();
            } catch (IOException | RuntimeException ignored) {
            }
        }).start();
    }

    @Override
    public void run() {
        try {
            if (netIF == null || netIF.isLoopback() || !netIF.isUp() || netIF.isVirtual()) {
                return;
            }
            List<InterfaceAddress> addresses = netIF.getInterfaceAddresses();
            for (InterfaceAddress intAddress : addresses) {
                try {
                    InetAddress address = intAddress.getAddress();
                    if (address instanceof Inet4Address) {
                        InetAddress broadcastAddress = intAddress.getBroadcast();
                        if (broadcastAddress instanceof Inet4Address) {
                            scanUDP((Inet4Address) broadcastAddress, (Inet4Address) address);
                        }
                        short subLen = intAddress.getNetworkPrefixLength();
                        if (subLen < 22) subLen = 23;
                        SubnetScanner subnetScanner = new SubnetScanner(address, port, subLen);
                        InetAddress server = subnetScanner.scan(subLen >= 24 ? 32 : 64);
                        if (server != null) {
                            String addressStr = server.getHostAddress();
                            if (addressStr != null) {
                                addressStr = addressStr.intern();
                                synchronized (serverAddresses) {
                                    serverAddresses.put(addressStr, server);
                                }
                            }
                            break;
                        }
                    }
                } catch (RuntimeException ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }
}
