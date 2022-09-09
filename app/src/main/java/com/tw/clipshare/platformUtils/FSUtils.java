package com.tw.clipshare.platformUtils;

import android.app.Activity;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Environment;
import android.widget.Toast;

import java.io.*;
import java.util.Random;

public class FSUtils extends AndroidUtils {
    private final long fileSize;
    private final String inFileName;
    private final InputStream inStream;
    private final String id;
    private String outFilePath;
    private String baseDirName;

    public FSUtils(Context context, Activity activity, String fileName, long fileSize, InputStream inStream) {
        super(context, activity);
        this.fileSize = fileSize;
        this.inFileName = fileName;
        this.inStream = inStream;
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

    public FSUtils(Context context, Activity activity) {
        this(context, activity, null, 0, null);
    }

    private String getDocumentDir() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            baseDirName = String.valueOf(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS));
        } else {
            baseDirName = String.valueOf(Environment.getExternalStorageDirectory());
        }
        return baseDirName + "/ClipShareDocuments";
    }

    public OutputStream getFileOutStream(String fileName) {
        final String dirName = getDocumentDir();
        File dir = new File(dirName);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                return null;
            }
        }
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

    public OutputStream getFileOutStream(String path, String baseName) {
        int ind = path != null ? path.indexOf('/') : -1;
        if (ind <= 0) return this.getFileOutStream(baseName);

        if (path.charAt(path.length() - 1) != '/') path += '/';
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
        path = dataDirName + "/" + path;
        File fp = new File(path);
        if (!fp.exists() && !fp.mkdirs()) {
            return null;
        }
        String filename = path + baseName;
        File f = new File(filename);
        this.outFilePath = filename;
        try {
            return new FileOutputStream(f);
        } catch (Exception ignored) {
            return null;
        }
    }

    public boolean finish() {
        final String dir = getDocumentDir() + "/";
        final String dataDirName = dir + this.id;
        File dataDir = new File(dataDirName);
        if (!dataDir.exists()) {
            return true;
        }
        String[] content = dataDir.list();
        if (content == null) {
            return true;
        }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            baseDirName = String.valueOf(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES));
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
        this.activity.runOnUiThread(() -> Toast.makeText(context, "Saved " + type + " to " + outFilePath.substring(baseDirName.length() + 1), Toast.LENGTH_SHORT).show());
        int dotIndex = outFilePath.lastIndexOf('.');
        if (dotIndex > 0) {
            String extension = outFilePath.substring(dotIndex + 1);
            String[] mediaExtensions = {"png", "jpg", "jpeg", "gif", "bmp", "webp", "tif", "tiff", "mp4", "mkv", "mov", "webm", "wmv", "flv", "avi"};
            for (String mediaExtension : mediaExtensions) {
                if (mediaExtension.equalsIgnoreCase(extension)) {
                    MediaScannerConnection.scanFile(this.activity.getApplicationContext(),
                            new String[]{outFilePath}, null, null);
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
}
