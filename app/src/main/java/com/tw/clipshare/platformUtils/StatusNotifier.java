package com.tw.clipshare.platformUtils;

public interface StatusNotifier {
    void setName(String name);

    void setStatus(int value);

    void reset();

    void finish();
}
