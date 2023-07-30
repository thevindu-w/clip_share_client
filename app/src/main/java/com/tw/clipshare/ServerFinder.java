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

    private ServerFinder(NetworkInterface netIF, Thread parent) {
        this.netIF = netIF;
        this.parent = parent;
    }

    public static List<InetAddress> find() {
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
                Runnable task = new ServerFinder(ni, curThread);
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

    private void scanUDP(InetAddress broadcastAddress) {
        new Thread(() -> {
            try {
                DatagramSocket socket = new DatagramSocket();
                byte[] buf = "in".getBytes();
                DatagramPacket pkt = new DatagramPacket(buf, buf.length, broadcastAddress, ClipShareActivity.PORT);
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
                    String received = new String(pkt.getData()).replace("\0", "");
                    if ("clip_share".equals(received)) {
                        InetAddress serverAddress = pkt.getAddress();
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
                    InetAddress broadcastAddress = intAddress.getBroadcast();
                    if (broadcastAddress != null) {
                        scanUDP(broadcastAddress);
                    }
                    InetAddress address = intAddress.getAddress();
                    if (address instanceof Inet4Address) {
                        short subLen = intAddress.getNetworkPrefixLength();
                        if (subLen < 22) subLen = 23;
                        SubnetScanner subnetScanner = new SubnetScanner(address, subLen);
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
