package com.tw.clipshare;

import android.content.Context;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.tw.clipshare.netConnection.ServerConnection;
import com.tw.clipshare.platformUtils.AndroidStatusNotifier;
import com.tw.clipshare.platformUtils.FSUtils;
import com.tw.clipshare.platformUtils.StatusNotifier;
import com.tw.clipshare.protocol.Proto_v1;
import com.tw.clipshare.protocol.ProtocolSelector;

import java.io.InputStream;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileSender {
    private final Object fileSendCntLock;
    private int fileSendingCount;
    private final LinkedList<PendingFile> pendingQueue;
    private final ClipShareActivity parent;
    private final Context context;
    private final ExecutorService executorService;
    private volatile boolean running, stopped;

    public FileSender(ClipShareActivity parent, Context context) {
        this.parent = parent;
        this.context = context;
        this.pendingQueue = new LinkedList<>();
        this.fileSendCntLock = new Object();
        this.fileSendingCount = 0;
        this.running = false;
        this.stopped = false;
        this.executorService = Executors.newSingleThreadExecutor();
        Runnable sendPendingFile = () -> {
            try {
                do {
                    if (!running) {
                        synchronized (executorService) {
                            if (!running) {
                                try {
                                    executorService.wait();
                                } catch (Exception ignored) {
                                }
                            }
                        }
                    }
                    while (running) {
                        PendingFile file = getNextFile();
                        sendFile(file.inputStream, file.name, file.sizeStr, file.serverAddress);
                    }
                } while (!stopped);
            } catch (Exception ignored) {
            }
        };
        executorService.submit(sendPendingFile);
    }

    public void start() {
        synchronized (executorService) {
            this.running = true;
            executorService.notifyAll();
        }
    }

    public void stop() {
        synchronized (executorService) {
            this.stopped = true;
            this.running = false;
            executorService.notifyAll();
        }
        try {
            this.pendingQueue.clear();
        } catch (Exception ignored) {
        }
    }

    public void submit(PendingFile file) {
        try {
            synchronized (pendingQueue) {
                pendingQueue.push(file);
                pendingQueue.notifyAll();
            }
        } catch (Exception ignored) {
        }
    }

    @NonNull
    private PendingFile getNextFile() {
        PendingFile file = null;
        while (file == null) {
            try {
                synchronized (pendingQueue) {
                    file = pendingQueue.pollFirst();
                    if (file == null) {
                        pendingQueue.wait();
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return file;
    }

    private void sendFile(InputStream fileInputStream, String fileName, String fileSizeStr, String serverAddress) {
        if (fileInputStream == null) {
            return;
        }
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Runnable sendFileFromURI = () -> {
            int notificationId;
            {
                Random rnd = new Random();
                notificationId = Math.abs(rnd.nextInt(Integer.MAX_VALUE - 1)) + 1;
            }
            NotificationManagerCompat notificationManager = null;
            synchronized (fileSendCntLock) {
                while (fileSendingCount > 1) {
                    try {
                        fileSendCntLock.wait();
                    } catch (InterruptedException ignored) {
                    }
                }
                fileSendingCount++;
            }
            try {
                parent.runOnUiThread(() -> {
                    try {
                        parent.output.setText(String.format("File : %s\nSize : %s bytes", fileName, fileSizeStr));
                    } catch (Exception ignored) {
                    }
                });
                long fileSize = Long.parseLong(fileSizeStr);

                notificationManager = NotificationManagerCompat.from(context);
                NotificationCompat.Builder builder = new NotificationCompat.Builder(context, ClipShareActivity.CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_upload_icon)
                        .setContentTitle("Sending file " + fileName)
                        .setContentText("0%")
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT);

                FSUtils utils = new FSUtils(context, parent, fileName, fileSize, fileInputStream);
                ServerConnection connection = parent.getServerConnection(serverAddress);
                StatusNotifier notifier = new AndroidStatusNotifier(parent, notificationManager, builder, notificationId);
                Proto_v1 proto = ProtocolSelector.getProto_v1(connection, utils, notifier);
                boolean status = proto != null && proto.sendFile();
                if (status)
                    parent.runOnUiThread(() -> Toast.makeText(context, "Sending " + fileName + " completed", Toast.LENGTH_SHORT).show());
                connection.close();
            } catch (Exception e) {
                parent.output.setText(String.format("Error %s", e.getMessage()));
            } finally {
                synchronized (fileSendCntLock) {
                    fileSendingCount--;
                    fileSendCntLock.notifyAll();
                }
                try {
                    if (notificationManager != null) {
                        NotificationManagerCompat finalNotificationManager1 = notificationManager;
                        parent.runOnUiThread(() -> {
                            try {
                                finalNotificationManager1.cancel(notificationId);
                            } catch (Exception ignored) {
                            }
                        });
                    }
                } catch (Exception ignored) {
                }
            }
        };
        executorService.submit(sendFileFromURI);
    }
}
