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

package com.tw.clipshare.platformUtils;

import android.app.Activity;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.widget.Toast;
import com.tw.clipshare.PendingFile;
import com.tw.clipshare.platformUtils.directoryTree.DirectoryTreeNode;
import java.io.*;
import java.util.LinkedList;
import java.util.Random;

/** Utility to access files */
public class FSUtils extends AndroidUtils {
  private long fileSize;
  private String inFileName;
  private InputStream inStream;
  private final String id;
  private String outFilePath;
  private String baseDirName;
  private final LinkedList<PendingFile> pendingFiles;
  private final DirectoryTreeNode directoryTree;
  private static long lastToastTime = 0;

  private FSUtils(
      Context context,
      Activity activity,
      LinkedList<PendingFile> pendingFiles,
      DirectoryTreeNode directoryTree) {
    super(context, activity);
    this.pendingFiles = pendingFiles;
    this.directoryTree = directoryTree;
    Random rnd = new Random();
    long idNum = Math.abs(rnd.nextLong());
    String id;
    File file;
    String dirName = getDocumentDir();
    do {
      id = Long.toString(idNum, 36);
      String tmpDirName = dirName + '/' + id;
      file = new File(tmpDirName);
      idNum++;
    } while (file.exists());
    this.id = id;
  }

  public FSUtils(Context context, Activity activity, LinkedList<PendingFile> pendingFiles) {
    this(context, activity, pendingFiles, null);
  }

  public FSUtils(Context context, Activity activity, DirectoryTreeNode directoryTree) {
    this(context, activity, null, directoryTree);
  }

  public FSUtils(Context context, Activity activity) {
    this(context, activity, null, null);
  }

  private String getDocumentDir() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      baseDirName =
          String.valueOf(
              Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS));
    } else {
      baseDirName = String.valueOf(Environment.getExternalStorageDirectory());
    }
    return baseDirName + "/ClipShareDocuments";
  }

  private String getDataDirPath(String path) {
    if (!path.isEmpty() && path.charAt(path.length() - 1) != '/') {
      path += '/';
    }
    final String dirName = getDocumentDir();
    File dir = new File(dirName);
    if (!dir.exists() && !dir.mkdirs()) {
      return null;
    }

    String dataDirName = dirName + "/" + this.id;
    File dataDir = new File(dataDirName);
    if (!dataDir.exists() && !dataDir.mkdirs()) {
      return null;
    }
    return dataDirName + "/" + path;
  }

  public OutputStream getFileOutStream(String fileName) {
    int base_ind = fileName.lastIndexOf('/') + 1;
    String baseName = fileName.substring(base_ind);
    String path = fileName.substring(0, base_ind);
    if (path.startsWith("../") || path.endsWith("/..") || path.contains("/../")) return null;
    path = getDataDirPath(path);
    if (path == null) return null;
    File fp = new File(path);
    if (!fp.exists() && !fp.mkdirs()) return null;
    String filename = path + baseName;
    File f = new File(filename);
    this.outFilePath = filename;
    try {
      return new FileOutputStream(f);
    } catch (Exception ignored) {
      return null;
    }
  }

  public boolean createDirectory(String dirPath) {
    dirPath = getDataDirPath(dirPath);
    if (dirPath == null) return false;
    File fp = new File(dirPath);
    if (fp.isDirectory()) return true;
    if (fp.exists()) return false;
    return fp.mkdirs();
  }

  public boolean finish() {
    final String dir = getDocumentDir() + "/";
    final String dataDirName = dir + this.id;
    File dataDir = new File(dataDirName);
    if (!dataDir.exists()) return true;
    String[] content = dataDir.list();
    if (content == null) return true;
    boolean status = true;
    for (String fileName : content) {
      File newFile = new File(dir + fileName);
      int pref = 1;
      while (newFile.exists()) {
        String newName = pref++ + "_" + fileName;
        newFile = new File(dir + newName);
      }
      File file = new File(dataDirName + "/" + fileName);
      status &= file.renameTo(newFile);
    }
    status &= dataDir.delete();
    return status;
  }

  public OutputStream getImageOutStream() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      baseDirName =
          String.valueOf(
              Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES));
    } else {
      baseDirName = String.valueOf(Environment.getExternalStorageDirectory());
    }
    final String dirName = baseDirName + "/ClipShareImages";
    File dir = new File(dirName);
    if (!dir.exists()) {
      if (!dir.mkdirs()) {
        return null;
      }
    }
    String fileName = Long.toString(System.currentTimeMillis(), 32) + ".png";
    String fileNameTmp = dirName + "/" + fileName;
    File file = new File(fileNameTmp);
    if (file.exists()) {
      int i = 1;
      while (file.exists()) {
        fileNameTmp = dirName + "/" + i + "_" + fileName;
        file = new File(fileNameTmp);
        i++;
      }
    }
    this.outFilePath = fileNameTmp;
    try {
      return new FileOutputStream(file);
    } catch (FileNotFoundException ignored) {
      return null;
    }
  }

  public void getFileDone(String type) {
    if (this.activity == null) return;
    long currTime = System.currentTimeMillis();
    if (currTime - lastToastTime > 2000) {
      lastToastTime = currTime;
      this.activity.runOnUiThread(
          () ->
              Toast.makeText(
                      context,
                      "Saved " + type + " to " + outFilePath.substring(baseDirName.length() + 1),
                      Toast.LENGTH_SHORT)
                  .show());
    }
    int dotIndex = outFilePath.lastIndexOf('.');
    if (dotIndex > 0) {
      String extension = outFilePath.substring(dotIndex + 1);
      String[] mediaExtensions = {
        "png", "jpg", "jpeg", "gif", "bmp", "webp", "heic", "tif", "tiff", "mp4", "mkv", "mov",
        "webm", "wmv", "flv", "avi"
      };
      for (String mediaExtension : mediaExtensions) {
        if (mediaExtension.equalsIgnoreCase(extension)) {
          MediaScannerConnection.scanFile(
              this.activity.getApplicationContext(), new String[] {outFilePath}, null, null);
          break;
        }
      }
    }
  }

  public String getFileName() {
    return this.inFileName;
  }

  public long getFileSize() {
    return this.fileSize;
  }

  public InputStream getFileInStream() {
    return this.inStream;
  }

  public int getRemainingFileCount(boolean includeLeafDirs) {
    if (this.pendingFiles != null) return this.pendingFiles.size();
    if (this.directoryTree != null) return this.directoryTree.getLeafCount(includeLeafDirs);
    return -1;
  }

  public int getRemainingFileCount() {
    return this.getRemainingFileCount(false);
  }

  public boolean prepareNextFile(boolean allowDirs) {
    try {
      if (this.directoryTree != null) {
        DirectoryTreeNode node = this.directoryTree.pop(allowDirs);
        this.inFileName = node.getFullName();
        this.fileSize = node.getFileSize();
        Uri uri = node.getUri();
        if (uri != null)
          this.inStream = activity.getContentResolver().openInputStream(node.getUri());
        else this.inStream = null;
        return true;
      }
      if (this.pendingFiles != null) {
        PendingFile pendingFile = this.pendingFiles.pop();
        this.inFileName = pendingFile.name;
        this.fileSize = pendingFile.size;
        this.inStream = pendingFile.inputStream;
        return true;
      }
    } catch (Exception ignored) {
    }
    return false;
  }

  public boolean prepareNextFile() {
    return this.prepareNextFile(false);
  }
}
