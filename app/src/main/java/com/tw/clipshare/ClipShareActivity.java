package com.tw.clipshare;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import com.tw.clipshare.netConnection.PlainConnection;
import com.tw.clipshare.netConnection.ServerConnection;
import com.tw.clipshare.platformUtils.AndroidStatusNotifier;
import com.tw.clipshare.platformUtils.AndroidUtils;
import com.tw.clipshare.platformUtils.FSUtils;
import com.tw.clipshare.platformUtils.StatusNotifier;
import com.tw.clipshare.protocol.Proto_v1;
import com.tw.clipshare.protocol.ProtocolSelector;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class SubnetScanner {

    private final byte[] addressBytes;
    private final InetAddress myAddress;
    private final int hostCnt;
    private final int port;
    private final Object lock;
    private volatile InetAddress serverAddress;

    public SubnetScanner(InetAddress address, short subLen, int port) {
        this.lock = new Object();
        this.port = port;
        this.myAddress = address;
        this.addressBytes = address.getAddress();
        this.hostCnt = (1 << (32 - subLen)) - 2;
        short hostLen = (short) (32 - subLen);
        for (int i = 3; i >= 0 && hostLen > 0; i--) {
            this.addressBytes[i] &= -(1 << hostLen);
            hostLen -= 8;
        }
    }

    private static InetAddress convertAddress(int addressInt) throws UnknownHostException {
        byte[] addressBytes = new byte[4];
        for (int i = 3; i >= 0; i--) {
            addressBytes[i] = (byte) (addressInt & 0xff);
            addressInt >>= 8;
        }
        return InetAddress.getByAddress(addressBytes);
    }

    public InetAddress scan(int threadCnt) {
        ExecutorService executor = Executors.newFixedThreadPool(threadCnt);
        int addressInt = 0;
        for (byte addressByte : addressBytes) {
            addressInt = (addressInt << 8) | (addressByte & 0xff);
        }
        addressInt++;
        int endAddress = addressInt + hostCnt;
        for (int i = 0; i < threadCnt; i++) {
            executor.submit(new IPScanner(addressInt++, endAddress, threadCnt));
        }
        while (this.serverAddress == null && !executor.isTerminated() && !Thread.interrupted()) {
            synchronized (this.lock) {
                try {
                    lock.wait(500);
                } catch (InterruptedException ex) {
                    break;
                }
            }
            executor.shutdown();
        }
        executor.shutdownNow();
        return this.serverAddress;
    }

    private class IPScanner implements Runnable {

        private final int addressEnd;
        private final int step;
        private int addressInt;

        IPScanner(int startAddress, int endAddress, int step) {
            this.step = step;
            this.addressInt = startAddress;
            this.addressEnd = endAddress;
        }

        @Override
        public void run() {
            while (!Thread.interrupted() && this.addressInt < this.addressEnd && serverAddress == null) {
                try {
                    InetAddress address = convertAddress(addressInt);
                    if (!address.equals(myAddress)) {
                        Socket clientSocket = new Socket();
                        clientSocket.connect(new InetSocketAddress(address, port), 200);
                        OutputStream sockOut = clientSocket.getOutputStream();
                        InputStream sockIn = clientSocket.getInputStream();
                        sockOut.write("in".getBytes());
                        byte[] len_bytes = new byte[4];
                        if (sockIn.read(len_bytes) < 4) {
                            sockOut.close();
                            sockIn.close();
                            clientSocket.close();
                            continue;
                        }
                        int infoLen = 0;
                        for (byte len_byte : len_bytes) {
                            infoLen = (infoLen << 8) | (len_byte & 0xff);
                        }
                        if (0 < infoLen && infoLen < 1024) {
                            byte[] info_bytes = new byte[infoLen];
                            if (sockIn.read(info_bytes) < infoLen) {
                                sockOut.close();
                                sockIn.close();
                                clientSocket.close();
                                continue;
                            }
                            sockOut.close();
                            sockIn.close();
                            clientSocket.close();
                            String info = new String(info_bytes);
                            if (info.equals("clip_server")) {
                                synchronized (lock) {
                                    serverAddress = address;
                                    lock.notifyAll();
                                }
                            }
                        } else {
                            sockOut.close();
                            sockIn.close();
                            clientSocket.close();
                        }
                    }
                } catch (IOException ex) { // Do not catch Interrupted exception in loop
                } finally {
                    addressInt += step;
                }
            }
        }
    }
}

class ServerFinder implements Runnable {

    private static InetAddress serverAddress;
    private static ExecutorService executorStatic;
    private final NetworkInterface netIF;
    //private static int runningCnt = 0;
    private final Thread parent;

    private ServerFinder(NetworkInterface netIF, Thread parent) {
        this.netIF = netIF;
        this.parent = parent;
    }

    public static InetAddress find() {
        serverAddress = null;
        try {
            if (executorStatic != null) executorStatic.shutdownNow();
            Enumeration<NetworkInterface> netIFEnum = NetworkInterface.getNetworkInterfaces();
            Object[] netIFList = Collections.list(netIFEnum).toArray();
            executorStatic = Executors.newFixedThreadPool(netIFList.length);
            ExecutorService executor = executorStatic;
            Thread curThread = Thread.currentThread();
            for (Object netIFList1 : netIFList) {
                NetworkInterface ni = (NetworkInterface) netIFList1;
                Runnable task = new ServerFinder(ni, curThread);
                executor.submit(task);
            }
            while (!executor.isTerminated()) {
                if (serverAddress != null) {
                    executor.shutdownNow();
                    break;
                }
                try {
                    //noinspection ResultOfMethodCallIgnored
                    executor.awaitTermination(500, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ignored) {
                    break;
                }
                executor.shutdown();
            }
            executor.shutdownNow();
        } catch (IOException | RuntimeException ex) {
            Log.d("Error", ex.getMessage());
            if (executorStatic != null) executorStatic.shutdownNow();
        }
        return serverAddress;
    }

    private void scanUDP(InetAddress broadcastAddress) {
        new Thread(() -> {
            try {
                DatagramSocket socket = new DatagramSocket();
                byte[] buf = "in".getBytes();
                DatagramPacket pkt = new DatagramPacket(buf, buf.length, broadcastAddress, ClipShareActivity.PORT);
                socket.send(pkt);
                buf = new byte[256];
                pkt = new DatagramPacket(buf, buf.length);
                socket.setSoTimeout(1000);
                try {
                    socket.receive(pkt);
                } catch (SocketTimeoutException ignored) {
                }
                String received = new String(pkt.getData()).replace("\0", "");
                if ("clip_share".equals(received)) {
                    serverAddress = pkt.getAddress();
                    parent.interrupt();
                }
            } catch (IOException | RuntimeException ignored) {
            }
        }).start();
    }

    @Override
    public void run() {
        try {
            if (netIF == null || netIF.isLoopback() || !netIF.isUp() || netIF.isVirtual()) {
                return;
            }
            List<InterfaceAddress> addresses = netIF.getInterfaceAddresses();
            for (InterfaceAddress intAddress : addresses) {
                try {
                    InetAddress broadcastAddress = intAddress.getBroadcast();
                    if (broadcastAddress != null) {
                        scanUDP(broadcastAddress);
                    }
                    InetAddress address = intAddress.getAddress();
                    if (address instanceof Inet4Address) {
                        short subLen = intAddress.getNetworkPrefixLength();
                        if (subLen < 22) subLen = 23;
                        SubnetScanner subnetScanner = new SubnetScanner(address, subLen, ClipShareActivity.PORT);
                        InetAddress server = subnetScanner.scan(subLen >= 24 ? 32 : 64);
                        if (server != null) {
                            serverAddress = server;
                            parent.interrupt();
                            break;
                        }
                    }
                } catch (RuntimeException ex) {
                    Log.d("Error", ex.getMessage());
                }
            }
        } catch (Exception ex) {
            Log.d("Error", ex.getMessage());
        }
    }
}

public class ClipShareActivity extends AppCompatActivity {
    public static final int WRITE_IMAGE = 222;
    public static final int WRITE_FILE = 223;
    public static final int CLIPBOARD_READ = 444;
    public static final int PORT = 4337;
    public static final String CHANNEL_ID = "upload_channel";
    private static final Object fileGetCntLock = new Object();
    private static int fileGettingCount = 0;
    public TextView output;
    private ActivityResultLauncher<Intent> fileSendActivityLauncher;
    private EditText editAddress;
    private Context context;
    private ArrayList<Uri> fileURIs;
    private FileSender fileSender;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.editAddress = findViewById(R.id.hostTxt);
        this.output = findViewById(R.id.txtOutput);
        this.context = getApplicationContext();

        Intent intent = getIntent();
        String type = intent.getType();
        if (type != null) {
            try {
                String action = intent.getAction();
                if (Intent.ACTION_SEND.equals(action)) {
                    output.setText(R.string.fileSelectedTxt);
                    this.fileURIs = new ArrayList<>(1);
                    this.fileURIs.add(intent.getParcelableExtra(Intent.EXTRA_STREAM));
                } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
                    this.fileURIs = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                    if (this.fileURIs != null)
                        output.setText(String.format(getString(R.string.filesSelectedTxt), this.fileURIs.size()));
                    else output.setText(R.string.noFilesTxt);
                } else {
                    this.fileURIs = null;
                    output.setText(R.string.noFilesTxt);
                }
            } catch (Exception e) {
                output.setText(e.getMessage());
            }
        } else {
            this.fileURIs = null;
        }

        this.fileSendActivityLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != Activity.RESULT_OK) {
                        return;
                    }
                    Intent intent1 = result.getData();
                    if (intent1 == null) {
                        return;
                    }
                    try {
                        ClipData clipData = intent1.getClipData();
                        if (clipData != null) {
                            int itemCount = clipData.getItemCount();
                            for (int cnt = 0; cnt < itemCount; cnt++) {
                                Uri uri = clipData.getItemAt(cnt).getUri();
                                sendFromURI(uri);
                            }
                        } else {
                            Uri uri = intent1.getData();
                            sendFromURI(uri);
                        }
                    } catch (Exception e) {
                        Log.d("Error", e.getMessage());
                        output.setText(String.format("Error %s", e.getMessage()));
                    }
                });

        output.setMovementMethod(new ScrollingMovementMethod());
        Button btnGet = findViewById(R.id.btnGetTxt);
        btnGet.setOnClickListener(view -> clkGetTxt());
        Button btnImg = findViewById(R.id.btnGetImg);
        btnImg.setOnClickListener(view -> checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, WRITE_IMAGE));
        Button btnFile = findViewById(R.id.btnGetFile);
        btnFile.setOnClickListener(view -> checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, WRITE_FILE));
        Button btnSendTxt = findViewById(R.id.btnSendTxt);
        btnSendTxt.setOnClickListener(view -> clkSendTxt());
        Button btnSendFile = findViewById(R.id.btnSendFile);
        btnSendFile.setOnClickListener(view -> clkSendFile());
        Button btnScanHost = findViewById(R.id.btnScanHost);
        btnScanHost.setOnClickListener(view -> clkScanBtn());
        SharedPreferences sharedPref = ClipShareActivity.this.getPreferences(Context.MODE_PRIVATE);
        editAddress.setText(sharedPref.getString("hostIP", ""));

        this.fileSender = new FileSender(this, ClipShareActivity.this);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                CharSequence name = getString(R.string.channel_name);
                String description = getString(R.string.channel_description);
                int importance = NotificationManager.IMPORTANCE_DEFAULT;
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
                channel.setDescription(description);
                // Register the channel with the system; you can't change the importance
                // or other notification behaviors after this
                NotificationManager notificationManager = getSystemService(NotificationManager.class);
                notificationManager.createNotificationChannel(channel);
            }
        } catch (Exception ex) {
            Log.d("Error", ex.getMessage());
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String type = intent.getType();
        if (type != null) {
            try {
                String action = intent.getAction();
                if (Intent.ACTION_SEND.equals(action)) {
                    output.setText(R.string.fileSelectedTxt);
                    this.fileURIs = new ArrayList<>(1);
                    this.fileURIs.add(intent.getParcelableExtra(Intent.EXTRA_STREAM));
                } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
                    this.fileURIs = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                    if (this.fileURIs != null)
                        output.setText(String.format(getString(R.string.filesSelectedTxt), this.fileURIs.size()));
                    else output.setText(R.string.noFilesTxt);
                }
            } catch (Exception e) {
                output.setText(e.getMessage());
            }
        }
    }

    @Override
    public void onDestroy() {
        fileSender.stop();
        super.onDestroy();
    }

    private void clkScanBtn() {
        new Thread(() -> {
            InetAddress serverAddress = ServerFinder.find();
            if (serverAddress != null) {
                runOnUiThread(() -> editAddress.setText(serverAddress.getHostAddress()));
            } else {
                runOnUiThread(() -> Toast.makeText(context, "Scan failed!", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    @Nullable
    private String getServerAddress() {
        try {
            String address = editAddress.getText().toString();
            if (!address.matches("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(\\.|$)){4}")) {
                Toast.makeText(ClipShareActivity.this, "Invalid address", Toast.LENGTH_SHORT).show();
                return null;
            }
            SharedPreferences sharedPref = ClipShareActivity.this.getPreferences(Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString("hostIP", address);
            editor.apply();
            return address;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void clkSendTxt() {
        String address = this.getServerAddress();
        if (address == null) return;
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Runnable sendClip = () -> {
            try {
                AndroidUtils utils = new AndroidUtils(context, ClipShareActivity.this);
                String clipDataString = utils.getClipboardText();
                if (clipDataString == null) return;
                ServerConnection connection = new PlainConnection(Inet4Address.getByName(address));
                Proto_v1 proto = ProtocolSelector.getProto_v1(connection, utils, null);
                if (proto == null) return;
                boolean status = proto.sendText(clipDataString);
                connection.close();
                if (!status) return;
                if (clipDataString.length() < 16384) output.setText(clipDataString);
                else output.setText(R.string.ReadClipSuccess);
            } catch (IOException | RuntimeException e) {
                Log.d("Testing_Error", e.getMessage());
                output.setText(String.format("Error %s", e.getMessage()));
            }
        };
        executorService.submit(sendClip);
    }

    private void clkSendFile() {
        if (this.fileURIs == null || this.fileURIs.isEmpty()) {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            fileSendActivityLauncher.launch(intent);
        } else {
            ArrayList<Uri> tmp = this.fileURIs;
            this.fileURIs = null;
            ExecutorService executorService = Executors.newSingleThreadExecutor();
            Runnable sendURIs = () -> {
                try {
                    for (Uri uri : tmp) {
                        sendFromURI(uri);
                    }
                } catch (Exception ignored) {
                }
            };
            executorService.submit(sendURIs);
        }
        this.fileSender.start();
    }

    private void sendFromURI(Uri uri) {
        try {
            String address = this.getServerAddress();
            if (address == null) return;

            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor.getCount() <= 0) {
                cursor.close();
                return;
            }
            cursor.moveToFirst();
            String fileName = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
            String fileSizeStr = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE));
            cursor.close();

            InputStream fileInputStream = getContentResolver().openInputStream(uri);

            PendingFile pendingFile = new PendingFile(fileInputStream, fileName, fileSizeStr, address);

            this.fileSender.submit(pendingFile);
        } catch (Exception ignored) {
        }
    }

    private void clkGetTxt() {
        String address = this.getServerAddress();
        if (address == null) return;
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Runnable getClip = () -> {
            try {
                AndroidUtils utils = new AndroidUtils(context, ClipShareActivity.this);
                ServerConnection connection = new PlainConnection(Inet4Address.getByName(address));
                Proto_v1 proto = ProtocolSelector.getProto_v1(connection, utils, null);
                if (proto == null) return;
                String text = proto.getText();
                connection.close();
                if (text == null) return;
                utils.setClipboardText(text);
                if (text.length() < 16384) output.setText(text);
                else output.setText(R.string.WriteClipSuccess);
            } catch (IOException | RuntimeException e) {
                output.setText(String.format("Error %s", e.getMessage()));
            }
        };
        executorService.submit(getClip);
    }

    private void clkGetImg() {
        String address = this.getServerAddress();
        if (address == null) return;
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Runnable getImg = () -> {
            try {
                FSUtils utils = new FSUtils(context, ClipShareActivity.this);
                ServerConnection connection = new PlainConnection(Inet4Address.getByName(address));
                Proto_v1 proto = ProtocolSelector.getProto_v1(connection, utils, null);
                boolean status = proto != null && proto.getImage();
                connection.close();
            } catch (IOException | RuntimeException e) {
                Log.d("Testing_Error", e.getMessage());
                output.setText(String.format("Error %s", e.getMessage()));
            }
        };
        executorService.submit(getImg);
    }

    private void clkGetFile() {
        String address = this.getServerAddress();
        if (address == null) return;

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Runnable getFile = () -> {
            int notificationId;
            {
                Random rnd = new Random();
                notificationId = Math.abs(rnd.nextInt(Integer.MAX_VALUE - 1)) + 1;
            }
            NotificationManagerCompat notificationManager = null;
            synchronized (fileGetCntLock) {
                while (fileGettingCount > 1) {
                    try {
                        fileGetCntLock.wait();
                    } catch (InterruptedException ignored) {
                    }
                }
                fileGettingCount++;
            }
            try {
                notificationManager = NotificationManagerCompat.from(context);
                NotificationCompat.Builder builder = new NotificationCompat.Builder(context, ClipShareActivity.CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_download_icon)
                        .setContentTitle("Getting file")
                        .setContentText("0%")
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT);

                FSUtils utils = new FSUtils(context, ClipShareActivity.this);
                ServerConnection connection = new PlainConnection(Inet4Address.getByName(address));
                StatusNotifier notifier = new AndroidStatusNotifier(ClipShareActivity.this, notificationManager, builder, notificationId);
                Proto_v1 proto = ProtocolSelector.getProto_v1(connection, utils, notifier);
                boolean status = proto != null && proto.getFile();
                connection.close();
                if (status) {
                    runOnUiThread(() -> {
                        try {
                            output.setText(R.string.recvAllFiles);
                        } catch (Exception ignored) {
                        }
                    });
                }
            } catch (IOException | RuntimeException e) {
                output.setText(String.format("Error %s", e.getMessage()));
            } finally {
                synchronized (fileGetCntLock) {
                    fileGettingCount--;
                    fileGetCntLock.notifyAll();
                }
                try {
                    if (notificationManager != null) {
                        NotificationManagerCompat finalNotificationManager = notificationManager;
                        runOnUiThread(() -> {
                            try {
                                finalNotificationManager.cancel(notificationId);
                            } catch (Exception ignored) {
                            }
                        });
                    }
                } catch (Exception ignored) {
                }
            }
        };
        executorService.submit(getFile);
    }

    public void checkPermission(String permission, int requestCode) {
        if (ContextCompat.checkSelfPermission(ClipShareActivity.this, permission) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(ClipShareActivity.this, new String[]{permission}, requestCode);
        } else {
            if (requestCode == WRITE_IMAGE) {
                clkGetImg();
            } else if (requestCode == WRITE_FILE) {
                clkGetFile();
            } else {
                Toast.makeText(ClipShareActivity.this, "Not Implemented", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == WRITE_IMAGE || requestCode == WRITE_FILE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (requestCode == WRITE_IMAGE) clkGetImg();
                else clkGetFile();
            } else {
                Toast.makeText(ClipShareActivity.this, "Storage Permission Denied", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == CLIPBOARD_READ) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(ClipShareActivity.this, "Not Implemented", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(ClipShareActivity.this, "Clipboard read Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}