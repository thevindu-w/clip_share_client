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

package com.tw.clipshare.platformUtils;

import androidx.annotation.Nullable;
import java.io.File;
import java.util.List;

public class DataContainer {
  private Object data;
  private String message;

  public void setData(Object data) {
    this.data = data;
  }

  @Nullable
  public String getString() {
    if (data instanceof String) {
      return (String) data;
    }
    return null;
  }

  @Nullable
  public List<File> getFiles() {
    if (data instanceof File file) {
      return List.of(file);
    }
    if (data instanceof List<?>) {
      for (Object obj : (List<?>) data) {
        if (!(obj instanceof File)) return null;
      }
      //noinspection unchecked
      return (List<File>) data;
    }
    return null;
  }

  @Nullable
  public String getMessage() {
    return this.message;
  }

  public void setMessage(String msg) {
    this.message = msg;
  }
}
