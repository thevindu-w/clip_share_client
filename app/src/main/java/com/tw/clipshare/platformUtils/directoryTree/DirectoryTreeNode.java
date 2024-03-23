package com.tw.clipshare.platformUtils.directoryTree;

import java.io.InputStream;
import java.util.LinkedList;

public abstract class DirectoryTreeNode {
  public final String name;
  private final Directory parent;

  DirectoryTreeNode(String name, Directory parent) {
    this.name = name;
    this.parent = parent;
  }

  public abstract int getLeafCount(boolean includeLeafDirs);

  public abstract long getFileSize();

  public abstract InputStream getInStream();

  public abstract DirectoryTreeNode pop(boolean includeDirs);

  public String getFullName() {
    LinkedList<DirectoryTreeNode> stack = new LinkedList<>();
    DirectoryTreeNode node = this;
    do {
      stack.push(node);
      node = node.parent;
    } while (node != null);
    StringBuilder builder = new StringBuilder();
    boolean first = true;
    while (!stack.isEmpty()) {
      if (!first) builder.append('/');
      first = false;
      builder.append(stack.pop().name);
    }
    return builder.toString();
  }
}
