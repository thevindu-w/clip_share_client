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
import android.os.*;
import android.provider.OpenableColumns;
import android.text.method.ScrollingMovementMethod;
import android.util.Patterns;
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
import androidx.documentfile.provider.DocumentFile;
import com.tw.clipshare.netConnection.*;
import com.tw.clipshare.platformUtils.AndroidStatusNotifier;
import com.tw.clipshare.platformUtils.AndroidUtils;
import com.tw.clipshare.platformUtils.FSUtils;
import com.tw.clipshare.platformUtils.StatusNotifier;
import com.tw.clipshare.platformUtils.directoryTree.Directory;
import com.tw.clipshare.platformUtils.directoryTree.DirectoryTreeNode;
import com.tw.clipshare.platformUtils.directoryTree.RegularFile;
import com.tw.clipshare.protocol.Proto;
import com.tw.clipshare.protocol.ProtocolSelector;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.ProtocolException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClipShareActivity extends AppCompatActivity {
  public static final int WRITE_IMAGE = 222;
  public static final int WRITE_FILE = 223;
  public static final String CHANNEL_ID = "notification_channel";
  private static final int AUTO_SEND_TEXT = 1;
  private static final int AUTO_SEND_FILES = 2;
  private static final Object fileGetCntLock = new Object();
  private static final Object fileSendCntLock = new Object();
  private static final Object settingsLock = new Object();
  private static int fileGettingCount = 0;
  private static int fileSendingCount = 0;
  private static boolean isSettingsLoaded = false;
  private String receivedURI;
  public TextView output;
  private ActivityResultLauncher<Intent> fileSelectActivityLauncher;
  private ActivityResultLauncher<Intent> folderSelectActivityLauncher;
  private ActivityResultLauncher<Intent> settingsActivityLauncher;
  private EditText editAddress;
  private Context context;
  private ArrayList<Uri> fileURIs;
  private Menu menu;
  private SwitchCompat tunnelSwitch;
  private LinearLayout openBrowserLayout;

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
      Settings st = Settings.getInstance();
      int icon_id = st.getSecure() ? R.drawable.ic_secure : R.drawable.ic_insecure;
      menu.findItem(R.id.action_secure)
          .setIcon(ContextCompat.getDrawable(ClipShareActivity.this, icon_id));

      MenuItem tunnelSwitch = menu.findItem(R.id.action_tunnel_switch);
      tunnelSwitch.setActionView(R.layout.tunnel_switch);
      this.tunnelSwitch = tunnelSwitch.getActionView().findViewById(R.id.tunnelSwitch);
      this.tunnelSwitch.setOnCheckedChangeListener(
          (switchView, isChecked) -> {
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
      settingsActivityLauncher.launch(settingsIntent);
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

    this.settingsActivityLauncher =
        registerForActivityResult(
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
                Settings st = Settings.getInstance();
                int icon_id = st.getSecure() ? R.drawable.ic_secure : R.drawable.ic_insecure;
                menu.findItem(R.id.action_secure)
                    .setIcon(ContextCompat.getDrawable(ClipShareActivity.this, icon_id));
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putString("settings", Settings.toString(st));
                editor.apply();
                runOnUiThread(
                    () -> Toast.makeText(context, "Saved settings", Toast.LENGTH_SHORT).show());
              } catch (Exception ignored) {
              }
            });

    this.fileSelectActivityLauncher =
        registerForActivityResult(
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
                this.fileURIs = getFileUris(intent1);
                clkSendFile();
              } catch (Exception e) {
                outputAppend("Error " + e.getMessage());
              }
            });

    this.folderSelectActivityLauncher =
        registerForActivityResult(
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
                DirectoryTreeNode root = ClipShareActivity.this.getDirectoryTree(intent1);
                clkSendFolder(root);
              } catch (Exception e) {
                outputAppend("Error " + e.getMessage());
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
    Button btnSendFolder = findViewById(R.id.btnSendFolder);
    btnSendFolder.setOnClickListener(view -> clkSendFolder(null));
    Button btnScanHost = findViewById(R.id.btnScanHost);
    btnScanHost.setOnClickListener(this::clkScanBtn);
    Button btnOpenLink = findViewById(R.id.btnOpenLink);
    btnOpenLink.setOnClickListener(view -> openInBrowser());
    openBrowserLayout = findViewById(R.id.layoutOpenBrowser);
    editAddress.setText(sharedPref.getString("serverIP", ""));
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

  private DirectoryTreeNode createDirectoryTreeNode(DocumentFile documentFile, Directory parent)
      throws FileNotFoundException {
    String name = documentFile.getName();
    if (!documentFile.isDirectory()) {
      Uri uri = documentFile.getUri();
      Cursor cursor = getContentResolver().query(uri, null, null, null, null);
      if (cursor.getCount() <= 0) {
        cursor.close();
        return null;
      }
      cursor.moveToFirst();
      String fileSizeStr = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE));
      cursor.close();
      InputStream fileInputStream = getContentResolver().openInputStream(uri);
      long fileSize = fileSizeStr != null ? Long.parseLong(fileSizeStr) : -1;
      return new RegularFile(name, fileSize, fileInputStream, parent);
    }
    DocumentFile[] children = documentFile.listFiles();
    Directory root = new Directory(name, children.length, parent);
    for (DocumentFile child : children) {
      DirectoryTreeNode node = createDirectoryTreeNode(child, root);
      if (node != null) {
        root.children.add(node);
      }
    }
    return root;
  }

  private DirectoryTreeNode getDirectoryTree(Intent intent) throws FileNotFoundException {
    Uri uri = intent.getData();
    DocumentFile documentFile = DocumentFile.fromTreeUri(this.context, uri);
    if (documentFile == null) return null;
    return createDirectoryTreeNode(documentFile, null);
  }

  @NonNull
  private static ArrayList<Uri> getFileUris(Intent intent) {
    ClipData clipData = intent.getClipData();
    ArrayList<Uri> uris;
    if (clipData != null) {
      int itemCount = clipData.getItemCount();
      uris = new ArrayList<>(itemCount);
      for (int count = 0; count < itemCount; count++) {
        Uri uri = clipData.getItemAt(count).getUri();
        uris.add(uri);
      }
    } else {
      Uri uri = intent.getData();
      uris = new ArrayList<>(1);
      uris.add(uri);
    }
    return uris;
  }

  @Override
  public void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    extractIntent(intent);
  }

  /**
   * Extract the file URIs or shared text from an intent.
   *
   * @param intent to extract data from
   */
  private void extractIntent(Intent intent) {
    String type = intent.getType();
    if (type != null) {
      try {
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
          Uri uri;
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
          } else {
            uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
          }
          if (uri != null) {
            this.fileURIs = new ArrayList<>(1);
            this.fileURIs.add(uri);
            runOnUiThread(() -> output.setText(R.string.fileSelectedTxt));
            autoSend(AUTO_SEND_FILES);
            return;
          }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
          ArrayList<Uri> uris;
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri.class);
          } else {
            uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
          }
          if (uris != null && !uris.isEmpty()) {
            this.fileURIs = uris;
            int count = this.fileURIs.size();
            runOnUiThread(
                () ->
                    output.setText(
                        context
                            .getResources()
                            .getQuantityString(R.plurals.filesSelectedTxt, count, count)));
            autoSend(AUTO_SEND_FILES);
            return;
          }
        }
        if (type.startsWith("text/")) {
          String text = intent.getStringExtra(Intent.EXTRA_TEXT);
          if (text != null) {
            AndroidUtils utils = new AndroidUtils(context, ClipShareActivity.this);
            utils.setClipboardText(text);
            runOnUiThread(() -> output.setText(R.string.textSelected));
            autoSend(AUTO_SEND_TEXT);
          }
        } else {
          this.fileURIs = null;
          runOnUiThread(() -> output.setText(R.string.noFilesTxt));
        }
      } catch (Exception e) {
        runOnUiThread(() -> output.setText(e.getMessage()));
      }
    } else {
      this.fileURIs = null;
    }
  }

  /**
   * Auto-sends the given type of data in a separate thread
   *
   * @param type data type to auto send
   */
  private void autoSend(int type) {
    ExecutorService autoSendExecutorService = Executors.newSingleThreadExecutor();
    Runnable runnableAutoSendText =
        () -> {
          try {
            synchronized (settingsLock) {
              while (!isSettingsLoaded) {
                try {
                  settingsLock.wait();
                } catch (InterruptedException ignored) {
                }
              }
            }
            Settings st = Settings.getInstance();
            switch (type) {
              case AUTO_SEND_TEXT:
                {
                  if (st.getAutoSendText()) clkSendTxt();
                  break;
                }
              case AUTO_SEND_FILES:
                {
                  if (st.getAutoSendFiles()) clkSendFile();
                  break;
                }
            }
          } catch (Exception e) {
            outputAppend("Auto send failed: " + e.getMessage());
          }
        };
    autoSendExecutorService.submit(runnableAutoSendText);
  }

  @SuppressWarnings("deprecation")
  private void vibrate() {
    try {
      if (!Settings.getInstance().getVibrate()) return;
      Vibrator vibrator;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        VibratorManager vibratorManager =
            (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
        vibrator = vibratorManager.getDefaultVibrator();
      } else {
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
      }

      final int duration = 100;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(
            VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
      } else {
        vibrator.vibrate(duration);
      }
    } catch (Exception ignored) {
    }
  }

  /**
   * Opens a ServerConnection. Returns null on error.
   *
   * @param addressStr IPv4 address of the server as a String in dotted decimal notation.
   * @return opened ServerConnection or null
   */
  @Nullable
  ServerConnection getServerConnection(@NonNull String addressStr) {
    int retries = 2;
    do {
      try {
        Settings settings = Settings.getInstance();
        if (tunnelSwitch != null && tunnelSwitch.isChecked()) {
          return new TunnelConnection(addressStr);
        } else if (settings.getSecure()) {
          InputStream caCertIn = settings.getCACertInputStream();
          InputStream clientCertKeyIn = settings.getCertInputStream();
          char[] clientPass = settings.getPasswd();
          if (clientCertKeyIn == null || clientPass == null) {
            return null;
          }
          String[] acceptedServers = settings.getTrustedList().toArray(new String[0]);
          return new SecureConnection(
              Inet4Address.getByName(addressStr),
              settings.getPortSecure(),
              caCertIn,
              clientCertKeyIn,
              clientPass,
              acceptedServers);
        } else {
          return new PlainConnection(Inet4Address.getByName(addressStr), settings.getPort());
        }
      } catch (Exception ignored) {
      }
    } while (retries-- > 0);
    return null;
  }

  /**
   * Wrapper to get connection and protocol selector
   *
   * @param address of the server
   * @param utils object or null
   * @param notifier object or null
   * @return a Proto object if success, or null otherwise
   */
  @Nullable
  private Proto getProtoWrapper(
      @NonNull String address, AndroidUtils utils, StatusNotifier notifier) {
    int retries = 1;
    do {
      try {
        ServerConnection connection = getServerConnection(address);
        if (connection == null) continue;
        Proto proto = ProtocolSelector.getProto(connection, utils, notifier);
        if (proto != null) return proto;
        connection.close();
      } catch (ProtocolException ex) {
        outputAppend(ex.getMessage());
        return null;
      } catch (Exception ignored) {
      }
    } while (retries-- > 0);
    outputAppend("Couldn't connect");
    return null;
  }

  private void openInBrowser() {
    try {
      try {
        Intent intent =
            new Intent(Intent.ACTION_VIEW, Uri.parse(ClipShareActivity.this.receivedURI));
        startActivity(intent);
      } catch (Exception ignored) {
      }
    } catch (Exception ignored) {
    }
  }

  private void clkScanBtn(View parent) {
    new Thread(
            () -> {
              try {
                Settings settings = Settings.getInstance();
                List<InetAddress> serverAddresses =
                    ServerFinder.find(settings.getPort(), settings.getPortUDP());
                if (!serverAddresses.isEmpty()) {
                  if (serverAddresses.size() == 1) {
                    InetAddress serverAddress = serverAddresses.get(0);
                    runOnUiThread(() -> editAddress.setText(serverAddress.getHostAddress()));
                  } else {
                    LayoutInflater inflater =
                        (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
                    View popupView =
                        inflater.inflate(R.layout.popup, findViewById(R.id.main_layout), false);
                    popupView
                        .findViewById(R.id.popupLinearWrap)
                        .setOnClickListener(v -> popupView.performClick());

                    int width = LinearLayout.LayoutParams.MATCH_PARENT;
                    int height = LinearLayout.LayoutParams.MATCH_PARENT;
                    boolean focusable = true; // lets taps outside the popup also dismiss it
                    final PopupWindow popupWindow =
                        new PopupWindow(popupView, width, height, focusable);
                    runOnUiThread(() -> popupWindow.showAtLocation(parent, Gravity.CENTER, 0, 0));

                    LinearLayout popupLayout = popupView.findViewById(R.id.popupLayout);
                    if (popupLayout == null) return;
                    View popupElemView;
                    TextView txtView;
                    for (InetAddress serverAddress : serverAddresses) {
                      popupElemView = View.inflate(this, R.layout.popup_elem, null);
                      txtView = popupElemView.findViewById(R.id.popElemTxt);
                      txtView.setText(serverAddress.getHostAddress());
                      txtView.setOnClickListener(
                          view -> {
                            runOnUiThread(() -> editAddress.setText(((TextView) view).getText()));
                            popupView.performClick();
                          });
                      popupLayout.addView(popupElemView);
                    }
                    popupView.setOnClickListener(v -> popupWindow.dismiss());
                  }
                } else {
                  runOnUiThread(
                      () ->
                          Toast.makeText(context, "No servers found!", Toast.LENGTH_SHORT).show());
                }
              } catch (Exception ignored) {
              }
            })
        .start();
  }

  /**
   * Gets the server's IPv4 address from the address input box
   *
   * @return IPv4 address of the server in dotted decimal notation as a String, or null
   */
  @Nullable
  private String getServerAddress() {
    try {
      String address = editAddress.getText().toString();
      if (!address.matches("^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)(\\.(?!$)|$)){4}$")) {
        Toast.makeText(ClipShareActivity.this, "Invalid address", Toast.LENGTH_SHORT).show();
        return null;
      }
      SharedPreferences sharedPref = ClipShareActivity.this.getPreferences(Context.MODE_PRIVATE);
      SharedPreferences.Editor editor = sharedPref.edit();
      editor.putString("serverIP", address);
      editor.apply();
      return address;
    } catch (Exception ignored) {
      return null;
    }
  }

  private void clkSendTxt() {
    try {
      runOnUiThread(
          () -> {
            openBrowserLayout.setVisibility(View.GONE);
            output.setText("");
          });
      String address = this.getServerAddress();
      if (address == null) return;
      ExecutorService executorService = Executors.newSingleThreadExecutor();
      Runnable sendClip =
          () -> {
            try {
              AndroidUtils utils = new AndroidUtils(context, ClipShareActivity.this);
              String clipDataString = utils.getClipboardText();
              if (clipDataString == null) return;
              Proto proto = getProtoWrapper(address, utils, null);
              if (proto == null) return;
              boolean status = proto.sendText(clipDataString);
              proto.close();
              if (!status) return;
              if (clipDataString.length() < 16384) outputAppend("Sent: " + clipDataString);
              else outputAppend("Sent: " + clipDataString.substring(0, 1024) + " ... (truncated)");
              this.vibrate();
            } catch (Exception e) {
              outputAppend("Error " + e.getMessage());
            }
          };
      executorService.submit(sendClip);
    } catch (Exception e) {
      outputAppend("Error " + e.getMessage());
    }
  }

  private void clkSendFile() {
    try {
      if (this.fileURIs == null || this.fileURIs.isEmpty()) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        fileSelectActivityLauncher.launch(intent);
      } else {
        ArrayList<Uri> tmp = this.fileURIs;
        this.fileURIs = null;
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Runnable sendURIs = () -> sendFromURIs(tmp);
        executorService.submit(sendURIs);
      }
    } catch (Exception ignored) {
      outputAppend("Error occurred");
    }
  }

  private void clkSendFolder(DirectoryTreeNode dirTree) {
    try {
      if (dirTree == null) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.setFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        folderSelectActivityLauncher.launch(intent);
      } else {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Runnable sendURIs = () -> sendFromDirectoryTree(dirTree);
        executorService.submit(sendURIs);
      }
    } catch (Exception ignored) {
      outputAppend("Error occurred");
    }
  }

  private void sendFromDirectoryTree(DirectoryTreeNode dirTree) {
    try {
      runOnUiThread(
          () -> {
            openBrowserLayout.setVisibility(View.GONE);
            output.setText("");
          });
      String address = this.getServerAddress();
      if (address == null) return;
      FSUtils utils = new FSUtils(context, ClipShareActivity.this, dirTree);
      Random rnd = new Random();
      int notificationId = Math.abs(rnd.nextInt(Integer.MAX_VALUE - 1)) + 1;
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
        NotificationCompat.Builder builder =
            new NotificationCompat.Builder(context, ClipShareActivity.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_upload_icon)
                .setContentTitle("Sending files");
        StatusNotifier notifier =
            new AndroidStatusNotifier(
                ClipShareActivity.this, notificationManager, builder, notificationId);
        boolean status = true;
        Proto proto = getProtoWrapper(address, utils, notifier);
        if (proto != null) {
          status &= proto.sendFile();
          proto.close();
        } else status = false;
        if (status) {
          runOnUiThread(
              () -> {
                try {
                  output.setText(R.string.sentAllFiles);
                } catch (Exception ignored) {
                }
              });
          this.vibrate();
        }
      } catch (Exception e) {
        outputAppend("Error " + e.getMessage());
      } finally {
        synchronized (fileSendCntLock) {
          fileSendingCount--;
          fileSendCntLock.notifyAll();
        }
        try {
          if (notificationManager != null) {
            NotificationManagerCompat finalNotificationManager = notificationManager;
            runOnUiThread(
                () -> {
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
      outputAppend("Error " + e.getMessage());
    }
  }

  /**
   * Sends files from a list of Uris
   *
   * @param uris of files
   */
  private void sendFromURIs(ArrayList<Uri> uris) {
    try {
      runOnUiThread(
          () -> {
            openBrowserLayout.setVisibility(View.GONE);
            output.setText("");
          });
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
        String fileName =
            cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
        String fileSizeStr = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE));
        cursor.close();

        InputStream fileInputStream = getContentResolver().openInputStream(uri);
        long fileSize = fileSizeStr != null ? Long.parseLong(fileSizeStr) : -1;
        PendingFile pendingFile = new PendingFile(fileInputStream, fileName, fileSize);
        pendingFiles.add(pendingFile);
      }
      FSUtils utils = new FSUtils(context, ClipShareActivity.this, pendingFiles);

      Random rnd = new Random();
      int notificationId = Math.abs(rnd.nextInt(Integer.MAX_VALUE - 1)) + 1;
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
        NotificationCompat.Builder builder =
            new NotificationCompat.Builder(context, ClipShareActivity.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_upload_icon)
                .setContentTitle("Sending files");
        StatusNotifier notifier =
            new AndroidStatusNotifier(
                ClipShareActivity.this, notificationManager, builder, notificationId);
        boolean status = true;
        while (utils.getRemainingFileCount() > 0) {
          Proto proto = getProtoWrapper(address, utils, notifier);
          if (proto == null) {
            status = false;
            break;
          }
          status &= proto.sendFile();
          proto.close();
        }
        if (status) {
          runOnUiThread(
              () -> {
                try {
                  output.setText(R.string.sentAllFiles);
                } catch (Exception ignored) {
                }
              });
          this.vibrate();
        }
      } catch (Exception e) {
        outputAppend("Error " + e.getMessage());
      } finally {
        synchronized (fileSendCntLock) {
          fileSendingCount--;
          fileSendCntLock.notifyAll();
        }
        try {
          if (notificationManager != null) {
            NotificationManagerCompat finalNotificationManager = notificationManager;
            runOnUiThread(
                () -> {
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
      outputAppend("Error " + e.getMessage());
    }
  }

  private void checkURL(String url) {
    if (!url.matches("^[a-z]{1,12}://.*$")) {
      url = "http://" + url;
    }
    if (Patterns.WEB_URL.matcher(url).matches()) {
      ClipShareActivity.this.receivedURI = url;
      runOnUiThread(() -> openBrowserLayout.setVisibility(View.VISIBLE));
    }
  }

  private void clkGetTxt() {
    try {
      runOnUiThread(
          () -> {
            openBrowserLayout.setVisibility(View.GONE);
            output.setText("");
          });
      String address = this.getServerAddress();
      if (address == null) return;
      ExecutorService executorService = Executors.newSingleThreadExecutor();
      Runnable getClip =
          () -> {
            try {
              AndroidUtils utils = new AndroidUtils(context, ClipShareActivity.this);
              Proto proto = getProtoWrapper(address, utils, null);
              if (proto == null) return;
              String text = proto.getText();
              proto.close();
              if (text == null) return;
              utils.setClipboardText(text);
              if (text.length() < 16384) outputAppend("Received: " + text);
              else outputAppend("Received: " + text.substring(0, 1024) + " ... (truncated)");
              checkURL(text);
              this.vibrate();
            } catch (Exception e) {
              outputAppend("Error " + e.getMessage());
            }
          };
      executorService.submit(getClip);
    } catch (Exception e) {
      outputAppend("Error " + e.getMessage());
    }
  }

  private void clkGetImg() {
    try {
      if (needsPermission(WRITE_IMAGE)) return;

      runOnUiThread(
          () -> {
            openBrowserLayout.setVisibility(View.GONE);
            output.setText("");
          });
      String address = this.getServerAddress();
      if (address == null) return;

      ExecutorService executorService = Executors.newSingleThreadExecutor();
      Runnable getImg =
          () -> {
            try {
              FSUtils utils = new FSUtils(context, ClipShareActivity.this);
              Proto proto = getProtoWrapper(address, utils, null);
              if (proto == null) return;
              boolean status = proto.getImage();
              proto.close();
              if (status) {
                this.vibrate();
              } else {
                runOnUiThread(
                    () ->
                        Toast.makeText(
                                ClipShareActivity.this, "Getting image failed", Toast.LENGTH_SHORT)
                            .show());
              }
            } catch (Exception e) {
              outputAppend("Error " + e.getMessage());
            }
          };
      executorService.submit(getImg);
    } catch (Exception e) {
      outputAppend("Error " + e.getMessage());
    }
  }

  private void clkGetFile() {
    try {
      if (needsPermission(WRITE_FILE)) return;

      runOnUiThread(
          () -> {
            openBrowserLayout.setVisibility(View.GONE);
            output.setText("");
          });
      String address = this.getServerAddress();
      if (address == null) return;

      ExecutorService executorService = Executors.newSingleThreadExecutor();
      Runnable getFile =
          () -> {
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
              NotificationCompat.Builder builder =
                  new NotificationCompat.Builder(context, ClipShareActivity.CHANNEL_ID)
                      .setSmallIcon(R.drawable.ic_download_icon)
                      .setContentTitle("Getting file");

              FSUtils utils = new FSUtils(context, ClipShareActivity.this);
              StatusNotifier notifier =
                  new AndroidStatusNotifier(
                      ClipShareActivity.this, notificationManager, builder, notificationId);
              Proto proto = getProtoWrapper(address, utils, notifier);
              if (proto == null) return;
              boolean status = proto.getFile();
              proto.close();
              if (status) {
                runOnUiThread(
                    () -> {
                      try {
                        output.setText(R.string.receiveAllFiles);
                      } catch (Exception ignored) {
                      }
                    });
                this.vibrate();
              }
            } catch (Exception e) {
              outputAppend("Error " + e.getMessage());
            } finally {
              synchronized (fileGetCntLock) {
                fileGettingCount--;
                fileGetCntLock.notifyAll();
              }
              try {
                if (notificationManager != null) {
                  NotificationManagerCompat finalNotificationManager = notificationManager;
                  runOnUiThread(
                      () -> {
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
    } catch (Exception e) {
      outputAppend("Error " + e.getMessage());
    }
  }

  /**
   * Checks if the app needs permission to write a file to storage. If the permission is not already
   * granted, this will request the permission from the user.
   *
   * @param requestCode to check if permission is needed
   * @return true if permission is required or false otherwise
   */
  private boolean needsPermission(int requestCode) {
    String permission = Manifest.permission.WRITE_EXTERNAL_STORAGE;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return false;
    if (ContextCompat.checkSelfPermission(ClipShareActivity.this, permission)
        == PackageManager.PERMISSION_DENIED) {
      ActivityCompat.requestPermissions(
          ClipShareActivity.this, new String[] {permission}, requestCode);
      return true;
    } else {
      return false;
    }
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);

    if (requestCode == WRITE_IMAGE || requestCode == WRITE_FILE) {
      if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        switch (requestCode) {
          case WRITE_IMAGE:
            {
              clkGetImg();
              break;
            }
          case WRITE_FILE:
            {
              clkGetFile();
              break;
            }
        }
      } else {
        Toast.makeText(ClipShareActivity.this, "Storage Permission Denied", Toast.LENGTH_SHORT)
            .show();
      }
    }
  }

  private void outputAppend(CharSequence text) {
    runOnUiThread(
        () -> {
          String newText = output.getText().toString() + text;
          output.setText(newText);
        });
  }
}
