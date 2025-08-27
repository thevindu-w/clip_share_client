/*
 * MIT License
 *
 * Copyright (c) 2022-2025 H. Thevindu J. Wijesekera
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
import java.util.LinkedList;

public abstract class DirectoryTreeNode {
  public String name;
  private final Directory parent;

  DirectoryTreeNode(String name, Directory parent) {
    this.name = name;
    this.parent = parent;
  }

  public abstract int getLeafCount(boolean includeLeafDirs);

  public abstract long getFileSize();

  public abstract Uri getUri();

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
