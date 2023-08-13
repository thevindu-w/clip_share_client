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

import com.tw.clipshare.netConnection.PlainConnection;
import com.tw.clipshare.netConnection.ServerConnection;
import com.tw.clipshare.protocol.Proto;
import com.tw.clipshare.protocol.ProtocolSelector;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class SubnetScanner {

    private final byte[] addressBytes;
    private final InetAddress myAddress;
    private final int hostCnt;
    private final Object lock;
    private final int port;
    private volatile InetAddress serverAddress;

    public SubnetScanner(InetAddress address, int port, short subLen) {
        this.lock = new Object();
        this.myAddress = address;
        this.port = port;
        this.addressBytes = address.getAddress();
        this.hostCnt = (1 << (32 - subLen)) - 2;
        short hostLen = (short) (32 - subLen);
        for (int i = 3; i >= 0 && hostLen > 0; i--) {
            this.addressBytes[i] &= -(1 << hostLen);
            hostLen -= 8;
        }
    }

    private static InetAddress convertAddress(int addressInt) throws UnknownHostException {
        byte[] addressBytes = new byte[4];
        for (int i = 3; i >= 0; i--) {
            addressBytes[i] = (byte) (addressInt & 0xff);
            addressInt >>= 8;
        }
        return InetAddress.getByAddress(addressBytes);
    }

    public InetAddress scan(int threadCnt) {
        ExecutorService executor = Executors.newFixedThreadPool(threadCnt);
        int addressInt = 0;
        for (byte addressByte : addressBytes) {
            addressInt = (addressInt << 8) | (addressByte & 0xff);
        }
        addressInt++;
        int endAddress = addressInt + hostCnt;
        for (int i = 0; i < threadCnt; i++) {
            executor.submit(new IPScanner(addressInt++, endAddress, port, threadCnt));
        }
        while (this.serverAddress == null && !executor.isTerminated() && !Thread.interrupted()) {
            synchronized (this.lock) {
                try {
                    lock.wait(500);
                } catch (InterruptedException ex) {
                    break;
                }
            }
            executor.shutdown();
        }
        executor.shutdownNow();
        return this.serverAddress;
    }

    private class IPScanner implements Runnable {

        private final int addressEnd;
        private final int step;
        private int addressInt;
        private final int port;

        IPScanner(int startAddress, int endAddress, int port, int step) {
            this.step = step;
            this.addressInt = startAddress;
            this.addressEnd = endAddress;
            this.port = port;
        }

        @Override
        public void run() {
            while (!Thread.interrupted() && this.addressInt < this.addressEnd && serverAddress == null) {
                try {
                    InetAddress address = convertAddress(addressInt);
                    if (!address.equals(myAddress)) {
                        ServerConnection con = new PlainConnection(address, port);
                        Proto pr = ProtocolSelector.getProto(con, null, null);
                        if (pr != null) {
                            String serverName = pr.checkInfo();
                            if ("clip_share".equals(serverName)) {
                                synchronized (lock) {
                                    serverAddress = address;
                                    lock.notifyAll();
                                }
                            }
                        }
                    }
                } catch (IOException ex) { // Do not catch Interrupted exception in loop
                } finally {
                    addressInt += step;
                }
            }
        }
    }
}
