package com.tw.clipshare;

import java.io.InputStream;

public class PendingFile {
    public final InputStream inputStream;
    public final String name;
    public final long size;

    public PendingFile(InputStream inputStream, String name, long size) {
        this.inputStream = inputStream;
        this.name = name;
        this.size = size;
    }
}
