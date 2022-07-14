package com.tw.clipshare;

import java.io.InputStream;

public class PendingFile {
    public final InputStream inputStream;
    public final String name;
    public final String sizeStr;
    public final String serverAddress;

    public PendingFile(InputStream inputStream, String name, String sizeStr, String serverAddress) {
        this.inputStream = inputStream;
        this.name = name;
        this.sizeStr = sizeStr;
        this.serverAddress = serverAddress;
    }
}
