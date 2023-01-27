package com.tw.clipshare;

import java.io.InputStream;

public class PendingFile {
    public final InputStream inputStream;
    public final String name;
    public final long size;
    public final String serverAddress;

    public PendingFile(InputStream inputStream, String name, long size, String serverAddress) {
        this.inputStream = inputStream;
        this.name = name;
        this.size = size;
        this.serverAddress = serverAddress;
    }
}
