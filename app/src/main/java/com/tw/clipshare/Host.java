package com.tw.clipshare;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Host {
  public String name;
  public final String address;

  public Host(String address, String name) {
    this.name = name;
    this.address = address;
  }

  public Host(String address) {
    this(address, null);
  }

  public Map<String, String> toMap() {
    Map<String, String> map = new HashMap<>(3);
    map.put("name", this.name);
    map.put("address", this.address);
    return map;
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof String) return other.equals(this.address);
    if (other instanceof Host host) return Objects.equals(this.address, host.address);
    return false;
  }
}
