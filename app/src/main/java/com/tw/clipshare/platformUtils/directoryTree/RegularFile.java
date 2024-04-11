package com.tw.clipshare.platformUtils.directoryTree;

import android.net.Uri;

public class RegularFile extends DirectoryTreeNode {

  public final Uri uri;
  public final long size;

  public RegularFile(String name, long size, Uri uri, Directory parent) {
    super(name, parent);
    this.size = size;
    this.uri = uri;
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
  public Uri getUri() {
    return this.uri;
  }

  @Override
  public DirectoryTreeNode pop(boolean includeDirs) {
    return this;
  }
}
