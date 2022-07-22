package com.tw.clipshare;

import java.util.ArrayList;
import java.util.List;

public class Settings {

    public static final Settings INSTANCE = new Settings();

    private boolean secure;
    private List<String> trustedList;

    public Settings(boolean secure) {
        this.secure = secure;
        this.trustedList = new ArrayList<>(1);
    }

    public Settings() {
        this(false);
    }

    public List<String> getTrustedList() {
        return this.trustedList;
    }

    public boolean getSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }
}
