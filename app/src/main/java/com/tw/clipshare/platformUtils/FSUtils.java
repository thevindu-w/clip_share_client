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

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.OpenableColumns;
import com.tw.clipshare.platformUtils.directoryTree.Directory;
import com.tw.clipshare.platformUtils.directoryTree.DirectoryTreeNode;
import com.tw.clipshare.platformUtils.directoryTree.RegularFile;
import java.io.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Random;

/** Utility to access files */
public class FSUtils extends AndroidUtils {
  private long fileSize;
  private String inFileName;
  private InputStream inStream;
  private final String id;
  private String outFilePath;
  private final LinkedList<RegularFile> regularFiles;
  private final LinkedList<RegularFile> loadedRegFiles;
  private final LinkedList<DirectoryTreeNode> loadedTreeNodes;
  private boolean loadingCompleted;
  private final DirectoryTreeNode directoryTree;
  private DataContainer dataContainer;

  private FSUtils(
      Context context,
      Activity activity,
      LinkedList<RegularFile> regularFiles,
      DirectoryTreeNode directoryTree) {
    super(context, activity);
    this.regularFiles = regularFiles;
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
    loadingCompleted = false;
    if (regularFiles != null) {
      this.loadedRegFiles = new LinkedList<>();
      this.loadRegularFiles();
    } else {
      this.loadedRegFiles = null;
    }
    if (directoryTree != null) {
      this.loadedTreeNodes = new LinkedList<>();
      this.loadTreeNodes();
    } else {
      this.loadedTreeNodes = null;
    }
  }

  public FSUtils(Context context, Activity activity, LinkedList<RegularFile> regularFiles) {
    this(context, activity, regularFiles, null);
  }

  public FSUtils(Context context, Activity activity, DirectoryTreeNode directoryTree) {
    this(context, activity, null, directoryTree);
  }

  public FSUtils(Context context, Activity activity) {
    this(context, activity, null, null);
  }

  private String getDocumentDir() {
    String baseDirName;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      baseDirName =
          Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
              .getAbsolutePath();
    } else {
      baseDirName = Environment.getExternalStorageDirectory().getAbsolutePath();
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
    ArrayList<File> files = new ArrayList<>(content.length);
    for (String fileName : content) {
      File newFile = new File(dir + fileName);
      int pref = 1;
      while (newFile.exists()) {
        String newName = pref++ + "_" + fileName;
        newFile = new File(dir + newName);
      }
      File file = new File(dataDirName + "/" + fileName);
      status &= file.renameTo(newFile);
      scanMediaFile(newFile.getAbsolutePath());
      if (newFile.isFile()) files.add(newFile);
    }
    if (status) dataContainer.setData(files);
    status &= dataDir.delete();
    return status;
  }

  public OutputStream getImageOutStream() {
    String baseDirName;
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
    String path;
    if ("image".equals(type)) {
      path = outFilePath;
      dataContainer.setData(new File(path));
    } else {
      path = getDocumentDir();
    }
    path = path.replaceFirst("^/storage/emulated/0", "Internal Storage");
    showToast("Saved " + type + " to " + path);
  }

  public void scanMediaFile(String filePath) {
    if (this.activity == null) return;
    int dotIndex = filePath.lastIndexOf('.');
    if (dotIndex <= 0) return;
    String extension = filePath.substring(dotIndex + 1);
    String[] mediaExtensions = {
      "png", "jpg", "jpeg", "gif", "bmp", "webp", "heic", "tif", "tiff", "mp4", "mkv", "mov",
      "webm", "wmv", "flv", "avi"
    };
    for (String mediaExtension : mediaExtensions) {
      if (mediaExtension.equalsIgnoreCase(extension)) {
        MediaScannerConnection.scanFile(
            this.activity.getApplicationContext(), new String[] {filePath}, null, null);
        break;
      }
    }
  }

  public void scanMediaFile() {
    scanMediaFile(outFilePath);
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
    if (this.regularFiles != null) {
      synchronized (loadedRegFiles) {
        return this.regularFiles.size() + this.loadedRegFiles.size();
      }
    }
    if (this.directoryTree != null) return this.directoryTree.getLeafCount(includeLeafDirs);
    return -1;
  }

  private boolean loadFileInfo(RegularFile file, ContentResolver resolver) {
    if (file.name == null) {
      Cursor cursor = resolver.query(file.getUri(), null, null, null, null);
      if (cursor.getCount() <= 0) {
        cursor.close();
        return true;
      }
      cursor.moveToFirst();
      file.name = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
      String sizeStr = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE));
      cursor.close();
      file.size = sizeStr != null ? Long.parseLong(sizeStr) : -1;
    }
    return false;
  }

  private void loadRegularFiles() {
    if (this.loadedRegFiles == null) return;
    ContentResolver contentResolver = activity.getContentResolver();
    Thread t =
        new Thread(
            () -> {
              try {
                RegularFile file;
                while ((file = this.regularFiles.peek()) != null) {
                  if (loadFileInfo(file, contentResolver)) break;
                  synchronized (loadedRegFiles) {
                    regularFiles.pop();
                    loadedRegFiles.add(file);
                    loadedRegFiles.notifyAll();
                  }
                }
              } catch (Exception ignored) {
              } finally {
                synchronized (loadedRegFiles) {
                  loadingCompleted = true;
                  loadedRegFiles.notifyAll();
                }
              }
            });
    try {
      t.setDaemon(true);
      t.setPriority(Thread.MIN_PRIORITY);
    } catch (Exception ignored) {
    }
    t.start();
  }

  private void recursivelyLoad(Directory root, ContentResolver contentResolver) {
    if (root.children.isEmpty()) {
      synchronized (loadedTreeNodes) {
        loadedTreeNodes.add(root);
        loadedTreeNodes.notifyAll();
      }
      return;
    }
    for (DirectoryTreeNode node : root.children) {
      if (node instanceof Directory dirNode) {
        recursivelyLoad(dirNode, contentResolver);
        continue;
      }
      if (loadFileInfo((RegularFile) node, contentResolver)) break;
      synchronized (loadedTreeNodes) {
        loadedTreeNodes.add(node);
        loadedTreeNodes.notifyAll();
      }
    }
  }

  private void loadTreeNodes() {
    if (this.loadedTreeNodes == null) return;
    ContentResolver contentResolver = activity.getContentResolver();
    Thread t =
        new Thread(
            () -> {
              try {
                recursivelyLoad((Directory) this.directoryTree, contentResolver);
              } catch (Exception ignored) {
              } finally {
                synchronized (loadedTreeNodes) {
                  loadingCompleted = true;
                  loadedTreeNodes.notifyAll();
                }
              }
            });
    try {
      t.setDaemon(true);
      t.setPriority(Thread.MIN_PRIORITY);
    } catch (Exception ignored) {
    }
    t.start();
  }

  private RegularFile getNextFile() throws InterruptedException {
    if (loadedRegFiles.isEmpty()) {
      synchronized (loadedRegFiles) {
        if (loadedRegFiles.isEmpty()) {
          if (loadingCompleted) throw new NoSuchElementException();
          loadedRegFiles.wait();
        }
      }
    }
    return loadedRegFiles.pop();
  }

  private DirectoryTreeNode getNextTreeNode(boolean allowDirs) throws InterruptedException {
    while (true) {
      if (loadedTreeNodes.isEmpty()) {
        synchronized (loadedTreeNodes) {
          if (loadedTreeNodes.isEmpty()) {
            if (loadingCompleted) throw new NoSuchElementException();
            loadedTreeNodes.wait();
          }
        }
      }
      DirectoryTreeNode node = loadedTreeNodes.pop();
      if (allowDirs || (node instanceof RegularFile)) return node;
    }
  }

  public boolean prepareNextFile(boolean allowDirs) {
    try {
      if (this.directoryTree != null) {
        DirectoryTreeNode node = this.getNextTreeNode(allowDirs);
        this.inFileName = node.getFullName();
        this.fileSize = node.getFileSize();
        Uri uri = node.getUri();
        if (uri != null) this.inStream = activity.getContentResolver().openInputStream(uri);
        else this.inStream = null;
        return false;
      }
      if (this.regularFiles != null) {
        RegularFile file = this.getNextFile();
        this.inFileName = file.name;
        this.fileSize = file.size;
        Uri uri = file.getUri();
        if (uri != null) this.inStream = activity.getContentResolver().openInputStream(uri);
        else this.inStream = null;
        return false;
      }
    } catch (Exception ignored) {
    }
    return true;
  }

  public void setDataContainer(DataContainer dataContainer) {
    this.dataContainer = dataContainer;
  }
}
