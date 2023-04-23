package com.tw.clipshare.netConnection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TunnelManager {

    private static final HashMap<String, Tunnel> tunnels = new HashMap<>(1);
    private static ExecutorService connectionExecutor = null;
    private static ExecutorService listenerExecutor = null;
    private static ServerSocket serverSocket = null;

    public static synchronized Socket getConnection(String address) {
        if (!tunnels.containsKey(address)) {
            return null;
        }
        Tunnel tunnel = tunnels.remove(address);
        if (tunnel == null) {
            return null;
        }
        Socket socket = null;
        try {
            socket = tunnel.releaseSocket();
        } catch (Exception ignored) {
        }
        return socket;
    }

    private static synchronized void putConnection(Socket connection) {
        String address = connection.getInetAddress().getHostAddress();
        if (tunnels.containsKey(address)) {
            Tunnel old = tunnels.get(address);
            if (old != null) {
                try {
                    old.close();
                } catch (Exception ignored) {
                }
            }
        }
        try {
            Tunnel tunnel = new Tunnel(connection);
            tunnels.put(address, tunnel);
            connectionExecutor.submit(tunnel);
        } catch (Exception ignored) {
        }
    }

    private static synchronized void removeConnection(Socket connection) {
        String address = connection.getInetAddress().getHostAddress();
        if (tunnels.containsKey(address)) {
            Tunnel tunnel = tunnels.get(address);
            if (tunnel != null) {
                try {
                    tunnel.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static void start() {
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (Exception ignored) {
        }
        try {
            serverSocket = new ServerSocket(4367);
            if (listenerExecutor != null) {
                listenerExecutor.shutdownNow();
            }
            listenerExecutor = Executors.newSingleThreadExecutor();
            connectionExecutor = Executors.newCachedThreadPool();
            Runnable listenerRunnable = () -> {
                try {
                    while (!Thread.interrupted()) {
                        Socket socket = serverSocket.accept();
                        putConnection(socket);
                    }
                    serverSocket.close();
                } catch (Exception ignored) {
                }
            };
            listenerExecutor.submit(listenerRunnable);
        } catch (Exception ignored) {
        }
    }

    public static void stop() {
        try {
            serverSocket.close();
        } catch (IOException ignored) {
        }
        try {
            if (listenerExecutor != null) listenerExecutor.shutdownNow();
            if (connectionExecutor != null) connectionExecutor.shutdown();
            tunnels.forEach((ip, tunnel) -> {
                try {
                    tunnel.close();
                } catch (Exception ignored) {
                }
            });
            tunnels.clear();
        } catch (Exception ignored) {
        }
    }

    private static class Tunnel extends Thread {

        private final Socket socket;
        private final InputStream inputStream;
        private final OutputStream outputStream;
        private boolean released;

        Tunnel(Socket socket) throws IOException {
            this.socket = socket;
            this.inputStream = socket.getInputStream();
            this.outputStream = socket.getOutputStream();
            released = false;
        }

        Socket releaseSocket() throws IOException {
            try {
                this.socket.setSoTimeout(5000);
            } catch (Exception ignored) {
            }
            synchronized (this) {
                outputStream.write(3);
                int read = inputStream.read();
                if (read != 4) {
                    socket.close();
                    throw new SocketException("Invalid client response");
                }
                if (this.released) {
                    throw new SocketException("Socket is already released");
                }
                this.released = true;
                this.interrupt();
            }
            return this.socket;
        }

        void close() throws IOException {
            synchronized (this) {
                this.released = true;
                this.interrupt();
                this.socket.close();
            }
        }

        @Override
        public void run() {
            try {
                socket.setSoTimeout(1000);
                while (!Thread.interrupted()) {
                    synchronized (this) {
                        if (this.released) {
                            break;
                        }
                    }
                    outputStream.write(1);
                    int read = inputStream.read();
                    if (read != 2) {
                        removeConnection(socket);
                        break;
                    }
                    if (Thread.interrupted()) break;
                    //noinspection BusyWait
                    Thread.sleep(2000);
                }
            } catch (Exception ignored) {
            }
        }
    }
}
