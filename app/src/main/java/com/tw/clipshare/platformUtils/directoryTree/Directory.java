package com.tw.clipshare.platformUtils.directoryTree;

import android.net.Uri;
import java.util.ArrayList;

public class Directory extends DirectoryTreeNode {
  public final ArrayList<DirectoryTreeNode> children;

  public Directory(String name, int size, Directory parent) {
    super(name, parent);
    this.children = new ArrayList<>(size);
  }

  @Override
  public int getLeafCount(boolean includeLeafDirs) {
    int leaves = 0;
    for (DirectoryTreeNode child : children) {
      leaves += child.getLeafCount(includeLeafDirs);
    }
    if (leaves == 0 && includeLeafDirs) leaves = 1;
    return leaves;
  }

  @Override
  public long getFileSize() {
    return -1;
  }

  @Override
  public Uri getUri() {
    return null;
  }

  @Override
  public DirectoryTreeNode pop(boolean includeDirs) {
    if (this.children.isEmpty() && includeDirs) return this;
    for (DirectoryTreeNode child : this.children) {
      if (child instanceof RegularFile) {
        this.children.remove(child);
        return child;
      }
      DirectoryTreeNode node = child.pop(includeDirs);
      if (node != null) return node;
    }
    return null;
  }
}
