/*
 * MIT License
 *
 * Copyright (c) 2022-2023 H. Thevindu J. Wijesekera
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
import android.view.*;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import com.tw.clipshare.netConnection.*;
import com.tw.clipshare.platformUtils.AndroidStatusNotifier;
import com.tw.clipshare.platformUtils.AndroidUtils;
import com.tw.clipshare.platformUtils.FSUtils;
import com.tw.clipshare.platformUtils.StatusNotifier;
import com.tw.clipshare.protocol.Proto;
import com.tw.clipshare.protocol.ProtocolSelector;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class SubnetScanner {

    private final byte[] addressBytes;
    private final InetAddress myAddress;
    private final int hostCnt;
    private final Object lock;
    private volatile InetAddress serverAddress;

    public SubnetScanner(InetAddress address, short subLen) {
        this.lock = new Object();
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
                        ServerConnection con = new PlainConnection(address);
                        Proto pr = ProtocolSelector.getProto(con, null, null);
                        if (pr != null) {
                            String serverName = pr.checkInfo();
                            if ("clip_share".equals(serverName)) {
                                synchronized (lock) {
                                    serverAddress = address;
                                    lock.notifyAll();
                                }
                            }
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

    private static final HashMap<String, InetAddress> serverAddresses = new HashMap<>(2);
    private static ExecutorService executorStatic;
    private final NetworkInterface netIF;
    private final Thread parent;

    private ServerFinder(NetworkInterface netIF, Thread parent) {
        this.netIF = netIF;
        this.parent = parent;
    }

    public static List<InetAddress> find() {
        try {
            synchronized (serverAddresses) {
                serverAddresses.clear();
            }
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
                if (!serverAddresses.isEmpty()) {
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
        } catch (IOException | RuntimeException ignored) {
            if (executorStatic != null) executorStatic.shutdownNow();
        }
        List<InetAddress> addresses;
        synchronized (serverAddresses) {
            addresses = new ArrayList<>(serverAddresses.values());
        }
        return addresses;
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
                int timeout = 1000;
                while (true) {
                    socket.setSoTimeout(timeout);
                    timeout = 200;
                    try {
                        socket.receive(pkt);
                    } catch (SocketTimeoutException ignored) {
                        break;
                    }
                    String received = new String(pkt.getData()).replace("\0", "");
                    if ("clip_share".equals(received)) {
                        InetAddress serverAddress = pkt.getAddress();
                        String addressStr = serverAddress.getHostAddress();
                        if (addressStr != null) {
                            addressStr = addressStr.intern();
                            synchronized (serverAddresses) {
                                serverAddresses.put(addressStr, serverAddress);
                            }
                        }
                    }
                }
                if (!serverAddresses.isEmpty()) parent.interrupt();
                socket.close();
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
                        SubnetScanner subnetScanner = new SubnetScanner(address, subLen);
                        InetAddress server = subnetScanner.scan(subLen >= 24 ? 32 : 64);
                        if (server != null) {
                            String addressStr = server.getHostAddress();
                            if (addressStr != null) {
                                addressStr = addressStr.intern();
                                synchronized (serverAddresses) {
                                    serverAddresses.put(addressStr, server);
                                }
                            }
                            break;
                        }
                    }
                } catch (RuntimeException ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }
}

public class ClipShareActivity extends AppCompatActivity {
    public static final int WRITE_IMAGE = 222;
    public static final int WRITE_FILE = 223;
    public static final int PORT = 4337;
    public static final String CHANNEL_ID = "upload_channel";
    private static final Object fileGetCntLock = new Object();
    private static final Object fileSendCntLock = new Object();
    private static final Object settingsLock = new Object();
    private static int fileGettingCount = 0;
    private static int fileSendingCount = 0;
    private static boolean isSettingsLoaded = false;
    public TextView output;
    private ActivityResultLauncher<Intent> activityLauncherForResult;
    private EditText editAddress;
    private Context context;
    private ArrayList<Uri> fileURIs;
    private Menu menu;
    private SwitchCompat switchCompat = null;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.action_bar, menu);
        this.menu = menu;
        try {
            synchronized (settingsLock) {
                while (!isSettingsLoaded) {
                    try {
                        settingsLock.wait();
                    } catch (InterruptedException ignored) {
                    }
                }
            }
            Settings st = Settings.getInstance(null);
            int icon_id = st.getSecure() ? R.drawable.ic_secure : R.drawable.ic_insecure;
            menu.findItem(R.id.action_secure).setIcon(ContextCompat.getDrawable(ClipShareActivity.this, icon_id));

            MenuItem tunnelSwitch = menu.findItem(R.id.action_tunnel_switch);
            tunnelSwitch.setActionView(R.layout.tunnel_switch);
            switchCompat = tunnelSwitch.getActionView().findViewById(R.id.tunnelSwitch);
            switchCompat.setOnCheckedChangeListener((switchView, isChecked) -> {
                if (isChecked) {
                    TunnelManager.start();
                } else {
                    TunnelManager.stop();
                }
            });
        } catch (Exception ignored) {
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemID = item.getItemId();
        if (itemID == R.id.action_settings) {
            Intent settingsIntent = new Intent(ClipShareActivity.this, SettingsActivity.class);
            settingsIntent.putExtra("settingsResult", 1);
            activityLauncherForResult.launch(settingsIntent);
        } else if (itemID == R.id.action_secure) {
            Toast.makeText(ClipShareActivity.this, "Change this in settings", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.editAddress = findViewById(R.id.hostTxt);
        this.output = findViewById(R.id.txtOutput);
        this.context = getApplicationContext();

        Intent intent = getIntent();
        if (intent != null) extractIntent(intent);

        SharedPreferences sharedPref = ClipShareActivity.this.getPreferences(Context.MODE_PRIVATE);

        this.activityLauncherForResult = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != Activity.RESULT_OK) {
                        return;
                    }
                    Intent intent1 = result.getData();
                    if (intent1 == null) {
                        return;
                    }
                    if (intent1.hasExtra("settingsResult")) {
                        Settings st = Settings.getInstance(null);
                        boolean sec = st.getSecure();
                        int icon_id = sec ? R.drawable.ic_secure : R.drawable.ic_insecure;
                        menu.findItem(R.id.action_secure).setIcon(ContextCompat.getDrawable(ClipShareActivity.this, icon_id));
                        SharedPreferences.Editor editor = sharedPref.edit();
                        try {
                            editor.putString("settings", Settings.toString(st));
                            editor.apply();
                        } catch (Exception ignored) {
                        }
                    } else {
                        try {
                            ClipData clipData = intent1.getClipData();
                            ArrayList<Uri> uris;
                            if (clipData != null) {
                                int itemCount = clipData.getItemCount();
                                uris = new ArrayList<>(itemCount);
                                for (int cnt = 0; cnt < itemCount; cnt++) {
                                    Uri uri = clipData.getItemAt(cnt).getUri();
                                    uris.add(uri);
                                }
                            } else {
                                Uri uri = intent1.getData();
                                uris = new ArrayList<>(1);
                                uris.add(uri);
                            }
                            this.fileURIs = uris;
                            clkSendFile();
                        } catch (Exception e) {
                            output.setText(String.format("Error %s", e.getMessage()));
                        }
                    }
                });

        output.setMovementMethod(new ScrollingMovementMethod());
        Button btnGet = findViewById(R.id.btnGetTxt);
        btnGet.setOnClickListener(view -> clkGetTxt());
        Button btnImg = findViewById(R.id.btnGetImg);
        btnImg.setOnClickListener(view -> clkGetImg());
        Button btnGetFile = findViewById(R.id.btnGetFile);
        btnGetFile.setOnClickListener(view -> clkGetFile());
        Button btnSendTxt = findViewById(R.id.btnSendTxt);
        btnSendTxt.setOnClickListener(view -> clkSendTxt());
        Button btnSendFile = findViewById(R.id.btnSendFile);
        btnSendFile.setOnClickListener(view -> clkSendFile());
        Button btnScanHost = findViewById(R.id.btnScanHost);
        btnScanHost.setOnClickListener(this::clkScanBtn);
        editAddress.setText(sharedPref.getString("hostIP", ""));
        try {
            Settings.getInstance(sharedPref.getString("settings", null));
        } catch (Exception ignored) {
        }
        isSettingsLoaded = true;
        synchronized (settingsLock) {
            settingsLock.notifyAll();
        }

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
        } catch (Exception ignored) {
        }
    }

    private void extractIntent(Intent intent) {
        String type = intent.getType();
        if (type != null) {
            try {
                String action = intent.getAction();
                if (Intent.ACTION_SEND.equals(action)) {
                    Uri extra = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                    if (extra != null) {
                        this.fileURIs = new ArrayList<>(1);
                        this.fileURIs.add(extra);
                        output.setText(R.string.fileSelectedTxt);
                        return;
                    }
                } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
                    ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                    if (uris != null && uris.size() > 0) {
                        this.fileURIs = uris;
                        int cnt = this.fileURIs.size();
                        output.setText(context.getResources().getQuantityString(R.plurals.filesSelectedTxt, cnt, cnt));
                        return;
                    }
                }
                if (type.startsWith("text/")) {
                    String text = intent.getStringExtra(Intent.EXTRA_TEXT);
                    if (text != null) {
                        AndroidUtils utils = new AndroidUtils(context, ClipShareActivity.this);
                        utils.setClipboardText(text);
                        output.setText(R.string.textSelected);
                    }
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
    }

    ServerConnection getServerConnection(String addressStr) {
        ServerConnection connection = null;
        try {
            Settings st = Settings.getInstance(null);
            if (switchCompat != null && switchCompat.isChecked()) {
                connection = new TunnelConnection(addressStr);
            } else if (st.getSecure()) {
                InputStream caCertIn = st.getCACertInputStream();
                InputStream clientCertKeyIn = st.getCertInputStream();
                char[] clientPass = st.getPasswd();
                if (clientCertKeyIn == null || clientPass == null) {
                    return null;
                }
                String[] acceptedServers = st.getTrustedList().toArray(new String[0]);
                connection = new SecureConnection(Inet4Address.getByName(addressStr), caCertIn, clientCertKeyIn, clientPass, acceptedServers);
            } else {
                connection = new PlainConnection(Inet4Address.getByName(addressStr));
            }
        } catch (Exception ignored) {
        }
        return connection;
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        extractIntent(intent);
    }

    private void clkScanBtn(View parent) {
        new Thread(() -> {
            try {
                List<InetAddress> serverAddresses = ServerFinder.find();
                if (!serverAddresses.isEmpty()) {
                    if (serverAddresses.size() == 1) {
                        InetAddress serverAddress = serverAddresses.get(0);
                        runOnUiThread(() -> editAddress.setText(serverAddress.getHostAddress()));
                    } else {
                        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
                        View popupView = inflater.inflate(R.layout.popup, findViewById(R.id.main_layout), false);
                        popupView.findViewById(R.id.popupLinearWrap).setOnClickListener(v -> popupView.performClick());

                        int width = LinearLayout.LayoutParams.MATCH_PARENT;
                        int height = LinearLayout.LayoutParams.MATCH_PARENT;
                        boolean focusable = true; // lets taps outside the popup also dismiss it
                        final PopupWindow popupWindow = new PopupWindow(popupView, width, height, focusable);
                        runOnUiThread(() -> popupWindow.showAtLocation(parent, Gravity.CENTER, 0, 0));

                        LinearLayout popupLayout = popupView.findViewById(R.id.popupLayout);
                        if (popupLayout == null) return;
                        View popupElemView;
                        TextView txtView;
                        for (InetAddress serverAddress : serverAddresses) {
                            popupElemView = View.inflate(this, R.layout.popup_elem, null);
                            txtView = popupElemView.findViewById(R.id.popElemTxt);
                            txtView.setText(serverAddress.getHostAddress());
                            txtView.setOnClickListener(view -> {
                                runOnUiThread(() -> editAddress.setText(((TextView) view).getText()));
                                popupView.performClick();
                            });
                            popupLayout.addView(popupElemView);
                        }
                        popupView.setOnClickListener(v -> popupWindow.dismiss());
                    }
                } else {
                    runOnUiThread(() -> Toast.makeText(context, "No servers found!", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception ignored) {
            }
        }).start();
    }

    @Nullable
    private String getServerAddress() {
        try {
            String address = editAddress.getText().toString();
            if (!address.matches("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(\\.|$)){4}$")) {
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
                ServerConnection connection = getServerConnection(address);
                Proto proto = ProtocolSelector.getProto(connection, utils, null);
                if (proto == null) {
                    runOnUiThread(() -> output.setText(R.string.couldNotConnect));
                    return;
                }
                boolean status = proto.sendText(clipDataString);
                connection.close();
                if (!status) return;
                if (clipDataString.length() < 16384) runOnUiThread(() -> output.setText(clipDataString));
                else
                    runOnUiThread(() -> output.setText(getString(R.string.truncated, clipDataString.substring(0, 1024))));
            } catch (Exception e) {
                runOnUiThread(() -> output.setText(String.format("Error %s", e.getMessage())));
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
            activityLauncherForResult.launch(intent);
        } else {
            ArrayList<Uri> tmp = this.fileURIs;
            this.fileURIs = null;
            ExecutorService executorService = Executors.newSingleThreadExecutor();
            Runnable sendURIs = () -> sendFromURIs(tmp);
            executorService.submit(sendURIs);
        }
    }

    private void sendFromURIs(ArrayList<Uri> uris) {
        try {
            String address = this.getServerAddress();
            if (address == null) return;

            LinkedList<PendingFile> pendingFiles = new LinkedList<>();
            for (Uri uri : uris) {
                Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                if (cursor.getCount() <= 0) {
                    cursor.close();
                    continue;
                }
                cursor.moveToFirst();
                String fileName = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
                String fileSizeStr = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE));
                cursor.close();

                InputStream fileInputStream = getContentResolver().openInputStream(uri);
                long fileSize = fileSizeStr != null ? Long.parseLong(fileSizeStr) : -1;
                PendingFile pendingFile = new PendingFile(fileInputStream, fileName, fileSize);
                pendingFiles.add(pendingFile);
            }
            FSUtils utils = new FSUtils(context, ClipShareActivity.this, pendingFiles);

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
                runOnUiThread(() -> output.setText(R.string.sendingFiles));
                notificationManager = NotificationManagerCompat.from(context);
                NotificationCompat.Builder builder = new NotificationCompat.Builder(context, ClipShareActivity.CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_upload_icon)
                        .setContentTitle("Sending files")
                        .setContentText("0%")
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT);
                StatusNotifier notifier = new AndroidStatusNotifier(ClipShareActivity.this, notificationManager, builder, notificationId);
                boolean status = true;
                while (utils.getRemainingFileCount() > 0) {
                    ServerConnection connection = this.getServerConnection(address);
                    Proto proto = ProtocolSelector.getProto(connection, utils, notifier);
                    if (proto == null) {
                        runOnUiThread(() -> output.setText(R.string.couldNotConnect));
                        return;
                    }
                    status &= proto.sendFile();
                    connection.close();
                }
                if (status) {
                    runOnUiThread(() -> {
                        try {
                            output.setText(R.string.sentAllFiles);
                        } catch (Exception ignored) {
                        }
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> output.setText(String.format("Error %s", e.getMessage())));
            } finally {
                synchronized (fileSendCntLock) {
                    fileSendingCount--;
                    fileSendCntLock.notifyAll();
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
        } catch (Exception e) {
            runOnUiThread(() -> output.setText(String.format("Error %s", e.getMessage())));
        }
    }

    private void clkGetTxt() {
        String address = this.getServerAddress();
        if (address == null) return;
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Runnable getClip = () -> {
            try {
                AndroidUtils utils = new AndroidUtils(context, ClipShareActivity.this);
                ServerConnection connection = getServerConnection(address);
                Proto proto = ProtocolSelector.getProto(connection, utils, null);
                if (proto == null) {
                    runOnUiThread(() -> output.setText(R.string.couldNotConnect));
                    return;
                }
                String text = proto.getText();
                connection.close();
                if (text == null) return;
                utils.setClipboardText(text);
                if (text.length() < 16384) runOnUiThread(() -> output.setText(text));
                else runOnUiThread(() -> output.setText(getString(R.string.truncated, text.substring(0, 1024))));
            } catch (Exception e) {
                runOnUiThread(() -> output.setText(String.format("Error %s", e.getMessage())));
            }
        };
        executorService.submit(getClip);
    }

    private void clkGetImg() {
        if (needsPermission(WRITE_IMAGE))
            return;

        String address = this.getServerAddress();
        if (address == null) return;

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Runnable getImg = () -> {
            try {
                FSUtils utils = new FSUtils(context, ClipShareActivity.this);
                ServerConnection connection = getServerConnection(address);
                Proto proto = ProtocolSelector.getProto(connection, utils, null);
                if (proto == null) {
                    runOnUiThread(() -> output.setText(R.string.couldNotConnect));
                    return;
                }
                boolean status = proto.getImage();
                connection.close();
                if (!status)
                    runOnUiThread(() -> Toast.makeText(ClipShareActivity.this, "Getting image failed", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> output.setText(String.format("Error %s", e.getMessage())));
            }
        };
        executorService.submit(getImg);
    }

    private void clkGetFile() {
        if (needsPermission(WRITE_FILE))
            return;

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
                ServerConnection connection = getServerConnection(address);
                StatusNotifier notifier = new AndroidStatusNotifier(ClipShareActivity.this, notificationManager, builder, notificationId);
                Proto proto = ProtocolSelector.getProto(connection, utils, notifier);
                if (proto == null) {
                    runOnUiThread(() -> output.setText(R.string.couldNotConnect));
                    return;
                }
                boolean status = proto.getFile();
                connection.close();
                if (status) {
                    runOnUiThread(() -> {
                        try {
                            output.setText(R.string.receiveAllFiles);
                        } catch (Exception ignored) {
                        }
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> output.setText(String.format("Error %s", e.getMessage())));
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

    private boolean needsPermission(int requestCode) {
        String permission = Manifest.permission.WRITE_EXTERNAL_STORAGE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return false;
        if (ContextCompat.checkSelfPermission(ClipShareActivity.this, permission) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(ClipShareActivity.this, new String[]{permission}, requestCode);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == WRITE_IMAGE || requestCode == WRITE_FILE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                switch (requestCode) {
                    case WRITE_IMAGE: {
                        clkGetImg();
                        break;
                    }
                    case WRITE_FILE: {
                        clkGetFile();
                        break;
                    }
                }
            } else {
                Toast.makeText(ClipShareActivity.this, "Storage Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}