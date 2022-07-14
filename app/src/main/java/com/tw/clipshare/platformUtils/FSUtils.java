package com.tw.clipshare.platformUtils;

import android.app.Activity;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Environment;
import android.widget.Toast;

import java.io.*;

public class FSUtils extends AndroidUtils {
    private final long fileSize;
    private final String inFileName;
    private final InputStream inStream;
    private String outFilePath;
    private String baseDirName;

    public FSUtils(Context context, Activity activity, String fileName, long fileSize, InputStream inStream) {
        super(context, activity);
        this.fileSize = fileSize;
        this.inFileName = fileName;
        this.inStream = inStream;
    }

    public FSUtils(Context context, Activity activity) {
        this(context, activity, null, 0, null);
    }

    public OutputStream getFileOutStream(String fileName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            baseDirName = String.valueOf(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS));
        } else {
            baseDirName = String.valueOf(Environment.getExternalStorageDirectory());
        }
        final String dirName = baseDirName + "/ClipShareDocuments";
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
