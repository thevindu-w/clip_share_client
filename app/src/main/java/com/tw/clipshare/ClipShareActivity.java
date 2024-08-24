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
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import com.tw.clipshare.netConnection.*;
import com.tw.clipshare.platformUtils.AndroidUtils;
import com.tw.clipshare.platformUtils.FSUtils;
import com.tw.clipshare.platformUtils.StatusNotifier;
import com.tw.clipshare.platformUtils.directoryTree.Directory;
import com.tw.clipshare.platformUtils.directoryTree.DirectoryTreeNode;
import com.tw.clipshare.platformUtils.directoryTree.RegularFile;
import com.tw.clipshare.protocol.Proto;
import com.tw.clipshare.protocol.Proto_v3;
import com.tw.clipshare.protocol.ProtocolSelector;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.ProtocolException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClipShareActivity extends AppCompatActivity {
  public static final int WRITE_IMAGE = 222;
  public static final int WRITE_FILE = 223;
  private static final String CHANNEL_ID = "notification_channel";
  public static final String PREFERENCES = "preferences";
  private static final int AUTO_SEND_TEXT = 1;
  private static final int AUTO_SEND_FILES = 2;
  private static final Object settingsLock = new Object();
  private static boolean isSettingsLoaded = false;
  private String receivedURI;
  public TextView output;
  private EditText editAddress;
  private Context context;
  private ArrayList<Uri> fileURIs;
  private Menu menu;
  private SwitchCompat tunnelSwitch;
  private LinearLayout openBrowserLayout;
  private int activeTasks = 0;
  private long lastActivityTime;
  private ExecutorService inactivityExecutor = null;
  private final ActivityResultLauncher<Intent> fileSelectActivityLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> {
            try {
              if (result.getResultCode() != Activity.RESULT_OK) {
                return;
              }
              Intent intent1 = result.getData();
              if (intent1 == null) {
                return;
              }
              this.fileURIs = getFileUris(intent1);
              clkSendFile();
            } catch (Exception e) {
              outputAppend("Error " + e.getMessage());
            } finally {
              ClipShareActivity.this.lastActivityTime = System.currentTimeMillis();
              endActiveTask();
            }
          });
  private final ActivityResultLauncher<Intent> folderSelectActivityLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> {
            try {
              if (result.getResultCode() != Activity.RESULT_OK) return;
              Intent intent1 = result.getData();
              if (intent1 == null) return;
              DirectoryTreeNode root = ClipShareActivity.this.getDirectoryTree(intent1);
              clkSendFolder(root);
            } catch (Exception e) {
              outputAppend("Error " + e.getMessage());
            } finally {
              ClipShareActivity.this.lastActivityTime = System.currentTimeMillis();
              endActiveTask();
            }
          });
  private final ActivityResultLauncher<Intent> settingsActivityLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> {
            try {
              if (result.getResultCode() != Activity.RESULT_OK) return;
              Intent intent1 = result.getData();
              if (intent1 == null) return;
              Settings st = Settings.getInstance();
              int icon_id = st.getSecure() ? R.drawable.ic_secure : R.drawable.ic_insecure;
              menu.findItem(R.id.action_secure)
                  .setIcon(ContextCompat.getDrawable(ClipShareActivity.this, icon_id));
              if (st.getCloseIfIdle()) closeIfIdle(st.getAutoCloseDelay() * 1000);
              else closeIfIdle(-1);
            } catch (Exception ignored) {
            } finally {
              ClipShareActivity.this.lastActivityTime = System.currentTimeMillis();
              endActiveTask();
            }
          });

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
      startActiveTask();
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

    output.setMovementMethod(new ScrollingMovementMethod());
    Button btnGet = findViewById(R.id.btnGetTxt);
    btnGet.setOnClickListener(view -> clkGetTxt());
    Button btnImg = findViewById(R.id.btnGetImg);
    btnImg.setOnClickListener(view -> clkGetImg());
    btnImg.setOnLongClickListener(this::longClkImg);
    Button btnGetFile = findViewById(R.id.btnGetFile);
    btnGetFile.setOnClickListener(view -> clkGetFile());
    Button btnSendTxt = findViewById(R.id.btnSendTxt);
    btnSendTxt.setOnClickListener(view -> clkSendTxt());
    Button btnSendFile = findViewById(R.id.btnSendFile);
    btnSendFile.setOnClickListener(view -> clkSendFile());
    btnSendFile.setOnLongClickListener(
        view -> {
          this.fileURIs = null;
          clkSendFile();
          return true;
        });
    Button btnSendFolder = findViewById(R.id.btnSendFolder);
    btnSendFolder.setOnClickListener(view -> clkSendFolder(null));
    Button btnScanHost = findViewById(R.id.btnScanHost);
    btnScanHost.setOnClickListener(this::clkScanBtn);
    Button btnOpenLink = findViewById(R.id.btnOpenLink);
    btnOpenLink.setOnClickListener(view -> openInBrowser());
    openBrowserLayout = findViewById(R.id.layoutOpenBrowser);

    SharedPreferences sharedPref =
        context.getSharedPreferences(ClipShareActivity.PREFERENCES, Context.MODE_PRIVATE);
    editAddress.setText(sharedPref.getString("serverIP", ""));
    Settings settings = null;
    try {
      settings = Settings.getInstance(sharedPref.getString("settings", null));
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
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
      }
    } catch (Exception ignored) {
    }
    try {
      if (settings != null && settings.getCloseIfIdle())
        closeIfIdle(settings.getAutoCloseDelay() * 1000);
    } catch (Exception ignored) {
    }
  }

  private void closeIfIdle(int delay) {
    synchronized (this) {
      if (inactivityExecutor != null) {
        inactivityExecutor.shutdownNow();
        inactivityExecutor = null;
      }
      if (delay < 0) return;
      inactivityExecutor = Executors.newSingleThreadExecutor();
    }
    this.lastActivityTime = System.currentTimeMillis();
    inactivityExecutor.submit(
        () -> {
          try {
            //noinspection InfiniteLoopStatement
            while (true) {
              long time = System.currentTimeMillis();
              long sleepTime = delay - time + ClipShareActivity.this.lastActivityTime + 50;
              if (sleepTime <= 0 || sleepTime > delay || ClipShareActivity.this.activeTasks > 0)
                sleepTime = delay;
              //noinspection BusyWait
              Thread.sleep(sleepTime);
              time = System.currentTimeMillis();
              if (ClipShareActivity.this.activeTasks == 0
                  && ClipShareActivity.this.lastActivityTime + delay < time) {
                ClipShareActivity.this.finish();
              }
            }
          } catch (Exception ignored) {
          }
        });
  }

  @Override
  public void onResume() {
    super.onResume();
    this.lastActivityTime = System.currentTimeMillis();
    endActiveTask();
  }

  @Override
  public void onPause() {
    startActiveTask();
    super.onPause();
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent event) {
    if (event.getAction() == MotionEvent.ACTION_DOWN) {
      this.lastActivityTime = System.currentTimeMillis();
    }
    return super.dispatchTouchEvent(event);
  }

  private void startActiveTask() {
    synchronized (this) {
      this.activeTasks++;
    }
  }

  private void endActiveTask() {
    synchronized (this) {
      this.activeTasks--;
      if (this.activeTasks < 0) this.activeTasks = 0;
    }
  }

  private DirectoryTreeNode createDirectoryTreeNode(DocumentFile documentFile, Directory parent) {
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
      long fileSize = fileSizeStr != null ? Long.parseLong(fileSizeStr) : -1;
      return new RegularFile(name, fileSize, uri, parent);
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

  private DirectoryTreeNode getDirectoryTree(Intent intent) {
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
    startActiveTask();
    new Thread(
            () -> {
              try {
                Settings settings = Settings.getInstance();
                List<InetAddress> serverAddresses =
                    ServerFinder.find(settings.getPort(), settings.getPortUDP());
                if (serverAddresses.isEmpty()) {
                  runOnUiThread(
                      () ->
                          Toast.makeText(context, "No servers found!", Toast.LENGTH_SHORT).show());
                  return;
                }
                if (serverAddresses.size() == 1) {
                  InetAddress serverAddress = serverAddresses.get(0);
                  runOnUiThread(() -> editAddress.setText(serverAddress.getHostAddress()));
                  return;
                }
                LayoutInflater inflater =
                    (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
                View popupView =
                    inflater.inflate(R.layout.popup_servers, findViewById(R.id.main_layout), false);
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
              } catch (Exception ignored) {
              } finally {
                ClipShareActivity.this.lastActivityTime = System.currentTimeMillis();
                endActiveTask();
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
      SharedPreferences sharedPref =
          context.getSharedPreferences(ClipShareActivity.PREFERENCES, Context.MODE_PRIVATE);
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
      startActiveTask();
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
              utils.vibrate();
            } catch (Exception e) {
              outputAppend("Error " + e.getMessage());
            }
          };
      executorService.submit(sendClip);
    } catch (Exception e) {
      outputAppend("Error " + e.getMessage());
    } finally {
      this.lastActivityTime = System.currentTimeMillis();
      endActiveTask();
    }
  }

  private void clkSendFile() {
    try {
      if (this.fileURIs == null || this.fileURIs.isEmpty()) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        fileSelectActivityLauncher.launch(intent);
        startActiveTask();
      } else {
        ArrayList<Uri> tmp = this.fileURIs;
        this.fileURIs = null;
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Runnable sendURIs = () -> sendFromURIs(tmp);
        executorService.submit(sendURIs);
      }
    } catch (Exception ignored) {
      outputAppend("Error occurred");
    } finally {
      this.lastActivityTime = System.currentTimeMillis();
    }
  }

  private void clkSendFolder(DirectoryTreeNode dirTree) {
    try {
      if (dirTree == null) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        folderSelectActivityLauncher.launch(intent);
        startActiveTask();
      } else {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Runnable sendURIs = () -> sendFromDirectoryTree(dirTree);
        executorService.submit(sendURIs);
      }
    } catch (Exception ignored) {
      outputAppend("Error occurred");
    } finally {
      this.lastActivityTime = System.currentTimeMillis();
    }
  }

  private void sendFromDirectoryTree(DirectoryTreeNode dirTree) {
    try {
      startActiveTask();
      runOnUiThread(
          () -> {
            openBrowserLayout.setVisibility(View.GONE);
            output.setText("");
          });
      String address = this.getServerAddress();
      if (address == null) return;
      FSUtils utils = new FSUtils(context, ClipShareActivity.this, dirTree);
      runOnUiThread(() -> output.setText(R.string.sendingFiles));
      if (utils.getRemainingFileCount() > 0) {
        if (handleTaskFromService(address, utils, PendingTask.SEND_FILES)) {
          outputAppend("Sending files\n");
        }
      }
    } catch (Exception e) {
      outputAppend("Error " + e.getMessage());
    } finally {
      this.lastActivityTime = System.currentTimeMillis();
      endActiveTask();
    }
  }

  /**
   * Sends files from a list of Uris
   *
   * @param uris of files
   */
  private void sendFromURIs(ArrayList<Uri> uris) {
    try {
      startActiveTask();
      runOnUiThread(
          () -> {
            openBrowserLayout.setVisibility(View.GONE);
            output.setText("");
          });
      String address = this.getServerAddress();
      if (address == null) {
        if (ClipShareActivity.this.fileURIs == null || ClipShareActivity.this.fileURIs.isEmpty()) {
          ClipShareActivity.this.fileURIs = uris;
        }
        return;
      }
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

        long fileSize = fileSizeStr != null ? Long.parseLong(fileSizeStr) : -1;
        PendingFile pendingFile = new PendingFile(uri, fileName, fileSize);
        pendingFiles.add(pendingFile);
      }

      boolean status = false;
      try {
        FSUtils utils = new FSUtils(context, ClipShareActivity.this, pendingFiles);
        if (utils.getRemainingFileCount() > 0) {
          if (handleTaskFromService(address, utils, PendingTask.SEND_FILES)) {
            status = true;
            outputAppend("Sending files\n");
          }
        }
      } catch (Exception e) {
        outputAppend("Error " + e.getMessage());
      }
      if (!status
          && (ClipShareActivity.this.fileURIs == null
              || ClipShareActivity.this.fileURIs.isEmpty())) {
        ClipShareActivity.this.fileURIs = uris;
      }
    } catch (Exception e) {
      outputAppend("Error " + e.getMessage());
    } finally {
      this.lastActivityTime = System.currentTimeMillis();
      endActiveTask();
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
      startActiveTask();
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
              utils.vibrate();
            } catch (Exception e) {
              outputAppend("Error " + e.getMessage());
            }
          };
      executorService.submit(getClip);
    } catch (Exception e) {
      outputAppend("Error " + e.getMessage());
    } finally {
      this.lastActivityTime = System.currentTimeMillis();
      endActiveTask();
    }
  }

  private void clkGetImg() {
    try {
      startActiveTask();
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
                utils.vibrate();
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
    } finally {
      this.lastActivityTime = System.currentTimeMillis();
      endActiveTask();
    }
  }

  /**
   * Long click gives additional options from protocol v3
   *
   * @noinspection SameReturnValue
   */
  private boolean longClkImg(View parent) {
    try {
      startActiveTask();
      if (needsPermission(WRITE_IMAGE)) return true;
      LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
      View popupView =
          inflater.inflate(R.layout.popup_display, findViewById(R.id.main_layout), false);
      int width = LinearLayout.LayoutParams.MATCH_PARENT;
      int height = LinearLayout.LayoutParams.MATCH_PARENT;
      boolean focusable = true; // lets taps outside the popup also dismiss it
      final PopupWindow popupWindow = new PopupWindow(popupView, width, height, focusable);
      popupWindow.showAtLocation(parent, Gravity.CENTER, 0, 0);

      Button btnGetCopiedImg = popupView.findViewById(R.id.btnGetCopiedImg);
      btnGetCopiedImg.setOnClickListener(
          view -> {
            getCopiedImg();
            popupWindow.dismiss();
          });

      EditText editDisplay = popupView.findViewById(R.id.editDisplay);
      if (editDisplay == null) return true;
      Button btnGetScreenshot = popupView.findViewById(R.id.btnGetScreenshot);
      btnGetScreenshot.setOnClickListener(
          view -> {
            getScreenshot(editDisplay);
            popupWindow.dismiss();
          });

      popupView.setOnClickListener(v -> popupWindow.dismiss());
    } catch (Exception ignored) {
    } finally {
      this.lastActivityTime = System.currentTimeMillis();
      endActiveTask();
    }
    return true;
  }

  private void getCopiedImg() {
    try {
      startActiveTask();
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
              if (!(proto instanceof Proto_v3)) {
                runOnUiThread(
                    () ->
                        Toast.makeText(
                                ClipShareActivity.this,
                                "Server doesn't support this method",
                                Toast.LENGTH_SHORT)
                            .show());
                proto.close();
                return;
              }
              Proto_v3 protoV3 = (Proto_v3) proto;
              boolean status = protoV3.getCopiedImage();
              proto.close();
              if (status) {
                utils.vibrate();
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
    } finally {
      this.lastActivityTime = System.currentTimeMillis();
      endActiveTask();
    }
  }

  private void getScreenshot(EditText editDisplay) {
    try {
      startActiveTask();
      runOnUiThread(
          () -> {
            openBrowserLayout.setVisibility(View.GONE);
            output.setText("");
          });
      String address = this.getServerAddress();
      if (address == null) return;
      String displayStr = editDisplay.getText().toString();
      int display;
      try {
        display = Integer.parseInt(displayStr);
      } catch (NumberFormatException ignored) {
        display = 0;
      }
      final int displayNum = display;
      ExecutorService executorService = Executors.newSingleThreadExecutor();
      Runnable getImg =
          () -> {
            try {
              FSUtils utils = new FSUtils(context, ClipShareActivity.this);
              Proto proto = getProtoWrapper(address, utils, null);
              if (proto == null) return;
              if (!(proto instanceof Proto_v3)) {
                runOnUiThread(
                    () ->
                        Toast.makeText(
                                ClipShareActivity.this,
                                "Server doesn't support this method",
                                Toast.LENGTH_SHORT)
                            .show());
                proto.close();
                return;
              }
              Proto_v3 protoV3 = (Proto_v3) proto;
              boolean status = protoV3.getScreenshot(displayNum);
              proto.close();
              if (status) {
                utils.vibrate();
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
    } finally {
      this.lastActivityTime = System.currentTimeMillis();
      endActiveTask();
    }
  }

  private void clkGetFile() {
    try {
      startActiveTask();
      if (needsPermission(WRITE_FILE)) return;

      runOnUiThread(
          () -> {
            openBrowserLayout.setVisibility(View.GONE);
            output.setText("");
          });
      String address = this.getServerAddress();
      if (address == null) return;
      Runnable getFile =
          () -> {
            try {
              FSUtils utils = new FSUtils(context, ClipShareActivity.this);
              if (handleTaskFromService(address, utils, PendingTask.GET_FILES)) {
                outputAppend("Getting file\n");
              }
            } catch (Exception e) {
              outputAppend("Error " + e.getMessage());
            }
          };
      ExecutorService executorService = Executors.newSingleThreadExecutor();
      executorService.submit(getFile);
    } catch (Exception e) {
      outputAppend("Error " + e.getMessage());
    } finally {
      this.lastActivityTime = System.currentTimeMillis();
      endActiveTask();
    }
  }

  private boolean handleTaskFromService(String address, AndroidUtils utils, int task) {
    try {
      Proto proto = getProtoWrapper(address, utils, null);
      if (proto == null) return false;
      FileService.addPendingTask(new PendingTask(proto, utils, task));
      Intent intent = new Intent(this, FileService.class);
      ContextCompat.startForegroundService(context, intent);
      return true;
    } catch (Exception e) {
      outputAppend("Error occurred: " + e.getMessage());
      return false;
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
