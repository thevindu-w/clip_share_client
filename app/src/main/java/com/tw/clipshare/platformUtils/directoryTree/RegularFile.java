package com.tw.clipshare.platformUtils.directoryTree;

import java.io.InputStream;

public class RegularFile extends DirectoryTreeNode {

  public final InputStream inputStream;
  public final long size;

  public RegularFile(String name, long size, InputStream inputStream, Directory parent) {
    super(name, parent);
    this.size = size;
    this.inputStream = inputStream;
  }

  @Override
  public int getLeafCount(boolean includeLeafDirs) {
    return 1;
  }

  @Override
  public long getFileSize() {
    return this.size;
  }

  @Override
  public InputStream getInStream() {
    return this.inputStream;
  }

  @Override
  public DirectoryTreeNode pop(boolean includeDirs) {
    return this;
  }
}
