/*
 * MIT License
 *
 * Copyright (c) 2022-2024 H. Thevindu J. Wijesekera
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

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
      Directory childDir = (Directory) child;
      DirectoryTreeNode node = childDir.pop(includeDirs);
      if (childDir.children.isEmpty()) this.children.remove(child);
      if (node != null) return node;
    }
    return null;
  }
}
