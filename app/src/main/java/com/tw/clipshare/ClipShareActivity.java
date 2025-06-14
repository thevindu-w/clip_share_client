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
import android.webkit.MimeTypeMap;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;
import com.tw.clipshare.netConnection.*;
import com.tw.clipshare.platformUtils.AndroidUtils;
import com.tw.clipshare.platformUtils.DataContainer;
import com.tw.clipshare.platformUtils.FSUtils;
import com.tw.clipshare.platformUtils.directoryTree.Directory;
import com.tw.clipshare.platformUtils.directoryTree.DirectoryTreeNode;
import com.tw.clipshare.platformUtils.directoryTree.RegularFile;
import com.tw.clipshare.protocol.Proto;
import com.tw.clipshare.protocol.ProtoV3;
import com.tw.clipshare.protocol.ProtocolSelector;
import java.io.File;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ProtocolException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class ClipShareActivity extends AppCompatActivity {
  public static final int WRITE_IMAGE = 222;
  public static final int WRITE_FILE = 223;
  private static final String CHANNEL_ID = "notification_channel";
  public static final String PREFERENCES = "preferences";
  private static final int AUTO_SEND_TEXT = 1;
  private static final int AUTO_SEND_FILES = 2;
  private static final byte GET_IMAGE = 5;
  private static final byte GET_COPIED_IMAGE = 6;
  private static final byte GET_SCREENSHOT = 7;
  public TextView output;
  private EditText editAddress;
  private Context context;
  private SharedPreferences sharedPref;
  private ArrayList<Uri> fileURIs;
  private Menu menu;
  private LinearLayout openBrowserLayout;
  private LinearLayout shareFileLayout;
  private LinearLayout viewFileLayout;
  private int activeTasks = 0;
  private long lastActivityTime;
  private ExecutorService inactivityExecutor = null;
  private ExecutorService fileUpdateExecutor = null;
  private final ActivityResultLauncher<Intent> fileSelectActivityLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result ->
              new Thread(
                      () -> {
                        try {
                          if (result.getResultCode() != Activity.RESULT_OK) return;
                          Intent intent1 = result.getData();
                          if (intent1 == null) return;
                          ClipShareActivity.this.fileURIs = getFileUris(intent1);
                          clkSendFile();
                        } catch (Exception e) {
                          outputAppend("Error " + e.getMessage());
                        } finally {
                          ClipShareActivity.this.lastActivityTime = System.currentTimeMillis();
                          endActiveTask();
                        }
                      })
                  .start());
  private final ActivityResultLauncher<Intent> folderSelectActivityLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result ->
              new Thread(
                      () -> {
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
                      })
                  .start());
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
      Settings st = Settings.getInstance();
      int icon_id = st.getSecure() ? R.drawable.ic_secure : R.drawable.ic_insecure;
      menu.findItem(R.id.action_secure)
          .setIcon(ContextCompat.getDrawable(ClipShareActivity.this, icon_id));
    } catch (Exception ignored) {
    }
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int itemID = item.getItemId();
    if (itemID == R.id.action_settings) {
      if (!Settings.isIsSettingsLoaded()) return true;
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
    this.sharedPref =
        context.getSharedPreferences(ClipShareActivity.PREFERENCES, Context.MODE_PRIVATE);

    output.setMovementMethod(new ScrollingMovementMethod());
    Button btnGet = findViewById(R.id.btnGetTxt);
    btnGet.setOnClickListener(view -> clkGetTxt());
    Button btnImg = findViewById(R.id.btnGetImg);
    btnImg.setOnClickListener(view -> getImageCommon(GET_IMAGE, 0));
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
    Button btnHistory = findViewById(R.id.btnHistory);
    btnHistory.setOnClickListener(this::clkHistoryBtn);
    openBrowserLayout = findViewById(R.id.layoutOpenBrowser);
    shareFileLayout = findViewById(R.id.layoutShareFile);
    viewFileLayout = findViewById(R.id.layoutViewFile);

    try {
      Settings.loadInstance(sharedPref.getString("settings", null));
    } catch (Exception ignored) {
    }
    try {
      List<String> servers = Settings.getInstance().getSavedServersList();
      if (!servers.isEmpty()) editAddress.setText(servers.get(servers.size() - 1));
    } catch (Exception ignored) {
    }

    Intent intent = getIntent();
    if (intent != null) extractIntent(intent);

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
      Settings settings = Settings.getInstance();
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

  private void listenFileServiceMessage() {
    if (fileUpdateExecutor != null) {
      fileUpdateExecutor.shutdown();
      fileUpdateExecutor.shutdownNow();
    }
    fileUpdateExecutor = Executors.newSingleThreadExecutor();
    Runnable runnable =
        () -> {
          try {
            do {
              DataContainer data = FileService.getNextMessage();
              if (data == null || data.getMessage() == null) {
                if (FileService.isStopped()) break;
                continue;
              }
              outputSetText(data.getMessage());
              List<File> files = data.getFiles();
              showShareButton(files, false);
              if (files != null && files.size() == 1) showViewFileButton(files.get(0));
              break;
            } while (true);
          } catch (Exception ignored) {
          }
        };
    fileUpdateExecutor.submit(runnable);
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
            //noinspection deprecation
            uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
          }
          if (uri != null) {
            this.fileURIs = new ArrayList<>(1);
            this.fileURIs.add(uri);
            outputSetText(R.string.fileSelectedTxt);
            autoSend(AUTO_SEND_FILES);
            return;
          }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
          ArrayList<Uri> uris;
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri.class);
          } else {
            //noinspection deprecation
            uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
          }
          if (uris != null && !uris.isEmpty()) {
            this.fileURIs = uris;
            int count = this.fileURIs.size();
            outputSetText(
                context.getResources().getQuantityString(R.plurals.filesSelectedTxt, count, count));
            autoSend(AUTO_SEND_FILES);
            return;
          }
        }
        if (type.startsWith("text/")) {
          String text = intent.getStringExtra(Intent.EXTRA_TEXT);
          if (text != null) {
            AndroidUtils utils = new AndroidUtils(context, ClipShareActivity.this);
            utils.setClipboardText(text);
            outputSetText(R.string.textSelected);
            autoSend(AUTO_SEND_TEXT);
          }
        } else {
          this.fileURIs = null;
          outputSetText(R.string.noFilesTxt);
        }
      } catch (Exception e) {
        outputAppend("Error " + e.getMessage());
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
            Settings st = Settings.getInstance();
            String address = this.getServerAddress();
            switch (type) {
              case AUTO_SEND_TEXT:
                {
                  if (st.getAutoSendText(address)) clkSendTxt();
                  break;
                }
              case AUTO_SEND_FILES:
                {
                  if (st.getAutoSendFiles(address)) clkSendFile();
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
        if (settings.getSecure()) {
          InputStream caCertIn = settings.getCACertInputStream();
          InputStream clientCertKeyIn = settings.getCertInputStream();
          char[] clientPass = settings.getPasswd();
          if (clientCertKeyIn == null || clientPass == null) {
            return null;
          }
          String[] acceptedServers = settings.getTrustedList().toArray(new String[0]);
          return new SecureConnection(
              InetAddress.getByName(addressStr),
              settings.getPortSecure(),
              caCertIn,
              clientCertKeyIn,
              clientPass,
              acceptedServers);
        } else {
          return new PlainConnection(InetAddress.getByName(addressStr), settings.getPort());
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
   * @return a Proto object if success, or null otherwise
   */
  @Nullable
  private Proto getProtoWrapper(@NonNull String address, AndroidUtils utils) {
    int retries = 1;
    do {
      try {
        ServerConnection connection = getServerConnection(address);
        if (connection == null) continue;
        Proto proto = ProtocolSelector.getProto(connection, utils, null);
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

  private void showShareButton(List<File> files, boolean isImage) {
    try {
      if (files == null || files.isEmpty()) return;
      boolean isSingle = files.size() == 1;
      Button btnShareFile = findViewById(R.id.btnShareFile);
      btnShareFile.setOnClickListener(
          view -> {
            try {
              Intent intent =
                  new Intent(isSingle ? Intent.ACTION_SEND : Intent.ACTION_SEND_MULTIPLE);
              intent.setType(isImage ? "image/png" : "application/octet-stream");
              ArrayList<Uri> uris = new ArrayList<>(files.size());
              for (File file : files) {
                uris.add(
                    FileProvider.getUriForFile(
                        context, context.getPackageName() + ".provider", file));
              }
              if (isSingle) intent.putExtra(Intent.EXTRA_STREAM, uris.get(0));
              else intent.putExtra(Intent.EXTRA_STREAM, uris);
              startActivity(intent);
            } catch (Exception ignored) {
            }
          });
      runOnUiThread(() -> shareFileLayout.setVisibility(View.VISIBLE));
    } catch (Exception ignored) {
    }
  }

  private void showViewFileButton(File file) {
    try {
      String filename = file.getName();
      String ext = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
      String mimetype = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
      if (mimetype == null) return;
      Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file);
      Button btnViewFile = findViewById(R.id.btnViewFile);
      btnViewFile.setOnClickListener(
          view -> {
            try {
              Intent intent = new Intent(Intent.ACTION_VIEW);
              intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
              intent.setDataAndType(uri, mimetype);
              startActivity(intent);
            } catch (Exception ignored) {
            }
          });
      runOnUiThread(() -> viewFileLayout.setVisibility(View.VISIBLE));
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
                List<String> addresses =
                    serverAddresses.stream()
                        .map(InetAddress::getHostAddress)
                        .collect(Collectors.toList());
                showAddressList(addresses, parent);
              } catch (Exception ignored) {
              } finally {
                ClipShareActivity.this.lastActivityTime = System.currentTimeMillis();
                endActiveTask();
              }
            })
        .start();
  }

  private void clkHistoryBtn(View parent) {
    startActiveTask();
    new Thread(
            () -> {
              try {
                Settings settings = Settings.getInstance();
                List<String> serverAddresses =
                    settings.getSavedServersList().stream()
                        .filter(addr -> !"0.0.0.0".equals(addr))
                        .collect(Collectors.toList());
                if (serverAddresses.isEmpty()) {
                  runOnUiThread(
                      () ->
                          Toast.makeText(context, "No saved servers!", Toast.LENGTH_SHORT).show());
                  return;
                }
                showAddressList(serverAddresses, parent);
              } catch (Exception ignored) {
              } finally {
                ClipShareActivity.this.lastActivityTime = System.currentTimeMillis();
                endActiveTask();
              }
            })
        .start();
  }

  private void showAddressList(List<String> addresses, View parent) {
    if (addresses.size() == 1) {
      String serverAddress = addresses.get(0);
      runOnUiThread(() -> editAddress.setText(serverAddress));
      return;
    }
    LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
    View popupView =
        inflater.inflate(R.layout.popup_servers, findViewById(R.id.main_layout), false);
    popupView.findViewById(R.id.popupLinearWrap).setOnClickListener(v -> popupView.performClick());

    final PopupWindow popupWindow =
        new PopupWindow(
            popupView,
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT,
            true);
    runOnUiThread(() -> popupWindow.showAtLocation(parent, Gravity.CENTER, 0, 0));

    LinearLayout popupLayout = popupView.findViewById(R.id.popupLayout);
    if (popupLayout == null) return;
    View popupElemView;
    TextView txtView;
    for (String serverAddress : addresses) {
      popupElemView = View.inflate(this, R.layout.popup_elem, null);
      txtView = popupElemView.findViewById(R.id.popElemTxt);
      txtView.setText(serverAddress);
      txtView.setOnClickListener(
          view -> {
            runOnUiThread(() -> editAddress.setText(((TextView) view).getText()));
            popupView.performClick();
          });
      popupLayout.addView(popupElemView);
    }
    popupView.setOnClickListener(v -> popupWindow.dismiss());
  }

  /**
   * Gets the server's IPv4 address from the address input box
   *
   * @return IPv4 address of the server in dotted decimal notation as a String, or null
   */
  @Nullable
  private String getServerAddress() {
    String address;
    try {
      address = editAddress.getText().toString();
      if (!Utils.isValidIP(address)) {
        Toast.makeText(ClipShareActivity.this, "Invalid address", Toast.LENGTH_SHORT).show();
        return null;
      }
    } catch (Exception ignored) {
      return null;
    }
    try {
      Settings settings = Settings.getInstance();
      if (!settings.getSaveServers()) return address;
      List<String> savedServers = settings.getSavedServersList();
      int ind = savedServers.lastIndexOf(address);
      if (ind == savedServers.size() - 1 && ind >= 0) return address;
      if (ind >= 0) savedServers.remove(address);
      if (savedServers.size() >= 50) savedServers.remove(0);
      savedServers.add(address);
      SharedPreferences.Editor editor = sharedPref.edit();
      editor.putString("settings", settings.toString());
      editor.apply();
    } catch (Exception ignored) {
    }
    return address;
  }

  private void clkSendTxt() {
    try {
      startActiveTask();
      outputReset();
      String address = this.getServerAddress();
      if (address == null) return;
      ExecutorService executorService = Executors.newSingleThreadExecutor();
      Runnable sendClip =
          () -> {
            try {
              AndroidUtils utils = new AndroidUtils(context, ClipShareActivity.this);
              String clipDataString = utils.getClipboardText();
              if (clipDataString == null) return;
              Proto proto = getProtoWrapper(address, utils);
              if (proto == null) return;
              boolean status = proto.sendText(clipDataString);
              proto.close();
              if (!status) return;
              if (clipDataString.length() < 16384) outputSetText("Sent: " + clipDataString);
              else outputSetText("Sent: " + clipDataString.substring(0, 1024) + " ... (truncated)");
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
      outputReset();
      String address = this.getServerAddress();
      if (address == null) return;
      FSUtils utils = new FSUtils(context, ClipShareActivity.this, dirTree);
      if (utils.getRemainingFileCount() > 0) {
        if (handleTaskFromService(address, utils, PendingTask.SEND_FILES)) {
          outputSetText(R.string.sendingFiles);
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
      outputReset();
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
            outputSetText(R.string.sendingFiles);
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
      String finalUrl = url;
      Button btnOpenLink = findViewById(R.id.btnOpenLink);
      btnOpenLink.setOnClickListener(
          view -> {
            try {
              Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl));
              startActivity(intent);
            } catch (Exception ignored) {
            }
          });
      runOnUiThread(() -> openBrowserLayout.setVisibility(View.VISIBLE));
    }
  }

  private void clkGetTxt() {
    try {
      startActiveTask();
      outputReset();
      String address = this.getServerAddress();
      if (address == null) return;
      ExecutorService executorService = Executors.newSingleThreadExecutor();
      Runnable getClip =
          () -> {
            try {
              AndroidUtils utils = new AndroidUtils(context, ClipShareActivity.this);
              Proto proto = getProtoWrapper(address, utils);
              if (proto == null) return;
              boolean status = proto.getText();
              proto.close();
              if (!status) return;
              String text = proto.dataContainer.getString();
              if (text == null) return;
              utils.setClipboardText(text);
              if (text.length() < 16384) outputSetText("Received: " + text);
              else outputSetText("Received: " + text.substring(0, 1024) + " ... (truncated)");
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

  /**
   * Long click gives additional options from protocol v3
   *
   * @noinspection SameReturnValue
   */
  private boolean longClkImg(View parent) {
    try {
      startActiveTask();
      if (needsPermission(0)) return true;
      LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
      View popupView =
          inflater.inflate(R.layout.popup_display, findViewById(R.id.main_layout), false);
      final PopupWindow popupWindow =
          new PopupWindow(
              popupView,
              LinearLayout.LayoutParams.MATCH_PARENT,
              LinearLayout.LayoutParams.MATCH_PARENT,
              true);
      popupWindow.showAtLocation(parent, Gravity.CENTER, 0, 0);

      Button btnGetCopiedImg = popupView.findViewById(R.id.btnGetCopiedImg);
      btnGetCopiedImg.setOnClickListener(
          view -> {
            getImageCommon(GET_COPIED_IMAGE, 0);
            popupWindow.dismiss();
          });

      EditText editDisplay = popupView.findViewById(R.id.editDisplay);
      if (editDisplay == null) return true;
      Button btnGetScreenshot = popupView.findViewById(R.id.btnGetScreenshot);
      btnGetScreenshot.setOnClickListener(
          view -> {
            int display;
            try {
              String displayStr = editDisplay.getText().toString();
              display = Integer.parseInt(displayStr);
              if (display < 0 || display >= 65536) display = 0;
            } catch (NumberFormatException ignored) {
              display = 0;
            }
            getImageCommon(GET_SCREENSHOT, display);
            popupWindow.dismiss();
          });

      popupView.setOnClickListener(v -> popupWindow.dismiss());
    } catch (Exception ignored) {
    }
    return true;
  }

  private void getImageCommon(int method, int display) {
    try {
      if (needsPermission(method == GET_IMAGE ? WRITE_IMAGE : 0)) return;
      outputReset();
      String address = this.getServerAddress();
      if (address == null) return;
      startActiveTask();
      ExecutorService executorService = Executors.newSingleThreadExecutor();
      Runnable getImg =
          () -> {
            try {
              FSUtils utils = new FSUtils(context, ClipShareActivity.this);
              Proto proto = getProtoWrapper(address, utils);
              if (proto == null) return;
              if (method != GET_IMAGE && !(proto instanceof ProtoV3)) {
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
              boolean status = false;
              if (method == GET_IMAGE) status = proto.getImage();
              else if (method == GET_COPIED_IMAGE) status = ((ProtoV3) proto).getCopiedImage();
              else if (method == GET_SCREENSHOT) status = ((ProtoV3) proto).getScreenshot(display);
              proto.close();
              if (status) {
                utils.vibrate();
                List<File> files = proto.dataContainer.getFiles();
                showShareButton(files, true);
                if (files != null && files.size() == 1) showViewFileButton(files.get(0));
              } else {
                runOnUiThread(
                    () ->
                        Toast.makeText(
                                ClipShareActivity.this, "Getting image failed", Toast.LENGTH_SHORT)
                            .show());
              }
            } catch (Exception e) {
              outputAppend("Error " + e.getMessage());
            } finally {
              this.lastActivityTime = System.currentTimeMillis();
              endActiveTask();
            }
          };
      executorService.submit(getImg);
    } catch (Exception e) {
      outputAppend("Error " + e.getMessage());
    }
  }

  private void clkGetFile() {
    try {
      startActiveTask();
      if (needsPermission(WRITE_FILE)) return;

      outputReset();
      String address = this.getServerAddress();
      if (address == null) return;
      Runnable getFile =
          () -> {
            try {
              FSUtils utils = new FSUtils(context, ClipShareActivity.this);
              if (handleTaskFromService(address, utils, PendingTask.GET_FILES)) {
                outputAppend("Getting files\n");
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
      Proto proto = getProtoWrapper(address, utils);
      if (proto == null) return false;
      FileService.addPendingTask(new PendingTask(proto, utils, task));
      Intent intent = new Intent(this, FileService.class);
      listenFileServiceMessage();
      ContextCompat.startForegroundService(context, intent);
      return true;
    } catch (Exception e) {
      outputAppend("Error " + e.getMessage());
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

    if (requestCode == 0 || requestCode == WRITE_IMAGE || requestCode == WRITE_FILE) {
      if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        switch (requestCode) {
          case WRITE_IMAGE:
            {
              getImageCommon(GET_IMAGE, 0);
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

  public void outputSetText(CharSequence text) {
    try {
      runOnUiThread(() -> output.setText(text));
    } catch (Exception ignored) {
    }
  }

  private void outputSetText(int resId) {
    try {
      runOnUiThread(() -> output.setText(resId));
    } catch (Exception ignored) {
    }
  }

  private void outputAppend(CharSequence text) {
    runOnUiThread(
        () -> {
          try {
            String newText = output.getText().toString() + text;
            output.setText(newText);
          } catch (Exception ignored) {
          }
        });
  }

  private void outputReset() {
    runOnUiThread(
        () -> {
          openBrowserLayout.setVisibility(View.GONE);
          shareFileLayout.setVisibility(View.GONE);
          viewFileLayout.setVisibility(View.GONE);
          output.setText("");
        });
  }
}
