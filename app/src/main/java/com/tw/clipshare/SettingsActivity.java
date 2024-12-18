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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.text.method.ScrollingMovementMethod;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.IdRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import com.tw.clipshare.netConnection.SecureConnection;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class SettingsActivity extends AppCompatActivity {
  private SwitchCompat secureSwitch;
  private Intent intent;
  private AtomicInteger idTLS;
  private AtomicInteger idAutoSend;
  private LinearLayout trustList;
  private LinearLayout autoSendTrustList;
  private EditText editPass;
  private TextView cnTxt;
  private TextView caCnTxt;
  private EditText editPort;
  private EditText editPortSecure;
  private EditText editPortUDP;
  private SwitchCompat autoSendTextSwitch;
  private SwitchCompat autoSendFileSwitch;
  private SwitchCompat vibrateSwitch;
  private SwitchCompat autoCloseSwitch;
  private EditText editAutoCloseDelay;
  private LinearLayout layoutAutoCloseDelay;
  private Settings settings;
  private final ActivityResultLauncher<Intent> clientActivityLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> {
            if (result.getResultCode() != Activity.RESULT_OK) return;
            Intent intent1 = result.getData();
            if (intent1 == null) return;
            try {
              Uri uri = intent1.getData();
              Cursor cursor = getContentResolver().query(uri, null, null, null, null);
              if (cursor.getCount() <= 0) {
                cursor.close();
                return;
              }
              cursor.moveToFirst();
              String fileSizeStr =
                  cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE));
              int size = Integer.parseInt(fileSizeStr);
              cursor.close();
              InputStream fileInputStream = getContentResolver().openInputStream(uri);
              char[] passwd = editPass.getText().toString().toCharArray();
              Settings st = Settings.getInstance();
              String cn = st.setCertPass(passwd, fileInputStream, size);
              if (cn != null) {
                SecureConnection.resetSSLContext();
                cnTxt.setText(cn);
              } else {
                Toast.makeText(
                        SettingsActivity.this, "Invalid client certificate", Toast.LENGTH_SHORT)
                    .show();
              }
            } catch (Exception ignored) {
            }
          });
  private final ActivityResultLauncher<Intent> caActivityLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> {
            if (result.getResultCode() != Activity.RESULT_OK) return;
            Intent intent1 = result.getData();
            if (intent1 == null) return;
            try {
              Uri uri = intent1.getData();
              Cursor cursor = getContentResolver().query(uri, null, null, null, null);
              if (cursor.getCount() <= 0) {
                cursor.close();
                return;
              }
              cursor.moveToFirst();
              String fileSizeStr =
                  cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE));
              int size = Integer.parseInt(fileSizeStr);
              cursor.close();
              InputStream fileInputStream = getContentResolver().openInputStream(uri);
              Settings st = Settings.getInstance();
              String CA_CN = st.setCACert(fileInputStream, size);
              if (CA_CN != null) {
                SecureConnection.resetSSLContext();
                caCnTxt.setText(CA_CN);
              } else {
                Toast.makeText(SettingsActivity.this, "Invalid CA certificate", Toast.LENGTH_SHORT)
                    .show();
              }
            } catch (Exception ignored) {
            }
          });
  private final ActivityResultLauncher<Intent> exportActivityLauncher =
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
              Uri uri = intent1.getData();
              if (uri == null) {
                return;
              }
              String jsonStr = settings.toString(true);
              try (OutputStream fileOutputStream = getContentResolver().openOutputStream(uri)) {
                fileOutputStream.write(jsonStr.getBytes(StandardCharsets.UTF_8));
                runOnUiThread(
                    () ->
                        Toast.makeText(
                                SettingsActivity.this, "Exported settings", Toast.LENGTH_SHORT)
                            .show());
              }
            } catch (Exception ignored) {
              runOnUiThread(
                  () ->
                      Toast.makeText(SettingsActivity.this, "Error occurred", Toast.LENGTH_SHORT)
                          .show());
            }
          });
  private final ActivityResultLauncher<Intent> importActivityLauncher =
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
              Uri uri = intent1.getData();
              if (uri == null) {
                return;
              }
              Cursor cursor = getContentResolver().query(uri, null, null, null, null);
              if (cursor.getCount() <= 0) {
                cursor.close();
                return;
              }
              cursor.moveToFirst();
              String fileSizeStr =
                  cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE));
              int size = Integer.parseInt(fileSizeStr);
              cursor.close();
              try (InputStream fileInputStream = getContentResolver().openInputStream(uri)) {
                byte[] data = new byte[size];
                if (fileInputStream.read(data) < size) return;
                String jsonStr = new String(data, StandardCharsets.UTF_8);
                Settings.loadInstance(jsonStr);
                editPort.setText(String.valueOf(settings.getPort()));
                editPortSecure.setText(String.valueOf(settings.getPortSecure()));
                editPortUDP.setText(String.valueOf(settings.getPortUDP()));
                String caCertCN1 = settings.getCACertCN();
                if (caCertCN1 != null) this.caCnTxt.setText(caCertCN1);
                String certCN1 = settings.getCertCN();
                if (certCN1 != null) this.cnTxt.setText(certCN1);
                List<String> servers1 = settings.getTrustedList();
                trustList.removeAllViews();
                for (String server : servers1) {
                  addRowToTrustList(false, server);
                }
                this.secureSwitch.setChecked(settings.getSecure());
                autoSendTextSwitch.setChecked(settings.getAutoSendText());
                autoSendFileSwitch.setChecked(settings.getAutoSendFiles());
                List<String> autoSendServers = settings.getAutoSendTrustedList();
                autoSendTrustList.removeAllViews();
                for (String server : autoSendServers) {
                  addRowToAutoSendTrustList(false, server);
                }
                vibrateSwitch.setChecked(settings.getVibrate());
                boolean autoClose = settings.getCloseIfIdle();
                autoCloseSwitch.setChecked(autoClose);
                editAutoCloseDelay.setText(String.valueOf(settings.getAutoCloseDelay()));
                if (autoClose) {
                  layoutAutoCloseDelay.setVisibility(View.VISIBLE);
                } else {
                  layoutAutoCloseDelay.setVisibility(View.GONE);
                }
                runOnUiThread(
                    () ->
                        Toast.makeText(
                                SettingsActivity.this, "Imported settings", Toast.LENGTH_SHORT)
                            .show());
              }
            } catch (Exception ignored) {
              runOnUiThread(
                  () ->
                      Toast.makeText(SettingsActivity.this, "Error occurred", Toast.LENGTH_SHORT)
                          .show());
            }
          });

  private void addRowToTrustList(boolean addToList, String name) {
    try {
      View trustServer = View.inflate(getApplicationContext(), R.layout.list_element, null);
      ImageButton delBtn = trustServer.findViewById(R.id.delBtn);
      TextView cnTxt = trustServer.findViewById(R.id.viewTxt);
      EditText cnEdit = trustServer.findViewById(R.id.editTxt);
      trustServer.setId(idTLS.getAndIncrement());
      Settings st = Settings.getInstance();
      List<String> servers = st.getTrustedList();
      cnTxt.setText(name != null ? name : "Server name");
      if (addToList) servers.add(cnTxt.getText().toString());
      trustList.addView(trustServer, 0);
      cnTxt.setTextColor(caCnTxt.getTextColors());
      cnEdit.setTextColor(caCnTxt.getTextColors());
      delBtn.setOnClickListener(
          view1 -> {
            try {
              if (servers.remove(cnTxt.getText().toString())) {
                trustList.removeView(trustServer);
              }
              if (servers.isEmpty()) {
                st.setSecure(false);
                SettingsActivity.this.secureSwitch.setChecked(false);
              }
            } catch (Exception ignored) {
            }
          });
      cnTxt.setOnClickListener(
          view1 -> {
            cnEdit.setText(cnTxt.getText());
            cnTxt.setVisibility(View.GONE);
            cnEdit.setVisibility(View.VISIBLE);
            cnEdit.requestFocus();
          });
      cnEdit.setOnFocusChangeListener(
          (view1, hasFocus) -> {
            if (!hasFocus) {
              CharSequence oldText = cnTxt.getText();
              CharSequence newText = cnEdit.getText();
              if (newText.length() > 0) cnTxt.setText(newText);
              cnEdit.setVisibility(View.GONE);
              cnTxt.setVisibility(View.VISIBLE);
              if (newText.length() > 0 && servers.remove(oldText.toString()))
                servers.add(newText.toString());
            }
          });
    } catch (Exception ignored) {
    }
  }

  private void addRowToAutoSendTrustList(boolean addToList, String address) {
    try {
      View trustServer = View.inflate(getApplicationContext(), R.layout.list_element, null);
      ImageButton delBtn = trustServer.findViewById(R.id.delBtn);
      TextView addressTxt = trustServer.findViewById(R.id.viewTxt);
      EditText addressEdit = trustServer.findViewById(R.id.editTxt);
      trustServer.setId(idAutoSend.getAndIncrement());
      Settings st = Settings.getInstance();
      List<String> servers = st.getAutoSendTrustedList();
      addressTxt.setText((address != null && address.matches(Consts.IPV4_REGEX)) ? address : "*");
      if (addToList) servers.add(addressTxt.getText().toString());
      autoSendTrustList.addView(trustServer, 0);
      addressTxt.setTextColor(caCnTxt.getTextColors());
      addressEdit.setTextColor(caCnTxt.getTextColors());
      delBtn.setOnClickListener(
          view1 -> {
            try {
              if (servers.remove(addressTxt.getText().toString())) {
                autoSendTrustList.removeView(trustServer);
              }
            } catch (Exception ignored) {
            }
          });
      addressTxt.setOnClickListener(
          view1 -> {
            addressEdit.setText(addressTxt.getText());
            addressTxt.setVisibility(View.GONE);
            addressEdit.setVisibility(View.VISIBLE);
            addressEdit.requestFocus();
          });
      addressEdit.setOnFocusChangeListener(
          (view1, hasFocus) -> {
            if (!hasFocus) {
              CharSequence oldText = addressTxt.getText();
              String newText = addressEdit.getText().toString();
              boolean isValid = "*".equals(newText) || newText.matches(Consts.IPV4_REGEX);
              if (isValid) addressTxt.setText(newText);
              else
                Toast.makeText(SettingsActivity.this, "Invalid IPv4 address", Toast.LENGTH_SHORT)
                    .show();
              addressEdit.setVisibility(View.GONE);
              addressTxt.setVisibility(View.VISIBLE);
              if (isValid && servers.remove(oldText.toString())) servers.add(newText);
            }
          });
    } catch (Exception ignored) {
    }
  }

  private void toggleLayout(ImageButton btn, LinearLayout layout) {
    Object tag = btn.getTag();
    if (tag instanceof Boolean && (Boolean) tag) {
      btn.setTag(false);
      layout.setVisibility(View.GONE);
      btn.setImageResource(android.R.drawable.arrow_down_float);
    } else {
      btn.setTag(true);
      layout.setVisibility(View.VISIBLE);
      btn.setImageResource(android.R.drawable.arrow_up_float);
    }
  }

  private void expandBlock(@IdRes int layoutId, @IdRes int buttonId) {
    LinearLayout layout = findViewById(layoutId);
    layout.setVisibility(View.GONE);
    ImageButton expandButton = findViewById(buttonId);
    expandButton.setImageResource(android.R.drawable.arrow_down_float);
    expandButton.setTag(false);
    expandButton.setOnClickListener(view -> toggleLayout((ImageButton) view, layout));
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.settings_activity);
    this.settings = Settings.getInstance();
    this.intent = getIntent();
    this.idTLS = new AtomicInteger(10000);
    this.idAutoSend = new AtomicInteger(20000);
    this.trustList = findViewById(R.id.trustedList);
    this.autoSendTrustList = findViewById(R.id.autoSendTrustedList);
    ImageButton addTrustedCNBtn = findViewById(R.id.addServerBtn);
    ImageButton addAutoSendServerBtn = findViewById(R.id.addAutoSendServerBtn);
    Button clientBrowseBtn = findViewById(R.id.btnImportCert);
    Button caBrowseBtn = findViewById(R.id.btnImportCACert);
    this.secureSwitch = findViewById(R.id.secureSwitch);
    this.editPass = findViewById(R.id.editCertPass);
    this.caCnTxt = findViewById(R.id.txtCACertName);
    this.cnTxt = findViewById(R.id.txtCertName);
    this.editPort = findViewById(R.id.editPort);
    this.editPortSecure = findViewById(R.id.editPortSecure);
    this.editPortUDP = findViewById(R.id.editPortUDP);
    this.autoSendTextSwitch = findViewById(R.id.autoSendTextSwitch);
    this.autoSendFileSwitch = findViewById(R.id.autoSendFileSwitch);
    this.vibrateSwitch = findViewById(R.id.vibrateSwitch);
    this.autoCloseSwitch = findViewById(R.id.autoCloseSwitch);
    this.editAutoCloseDelay = findViewById(R.id.editAutoCloseDelay);
    this.layoutAutoCloseDelay = findViewById(R.id.layoutAutoCloseDelay);

    expandBlock(R.id.autoSendLayout, R.id.expandAutoSendBtn);
    expandBlock(R.id.savedAddressLayout, R.id.expandSavedAddressBtn);
    expandBlock(R.id.secureModeLayout, R.id.expandSecureModeBtn);
    expandBlock(R.id.otherSettingsLayout, R.id.expandOtherSettingsBtn);

    this.secureSwitch.setOnClickListener(
        view -> {
          if (!SettingsActivity.this.secureSwitch.isChecked()) {
            settings.setSecure(false);
            return;
          }
          if (settings.getCACertCN() == null) {
            Toast.makeText(SettingsActivity.this, "No CA certificate", Toast.LENGTH_SHORT).show();
            SettingsActivity.this.secureSwitch.setChecked(false);
            settings.setSecure(false);
            return;
          }
          if (settings.getCertCN() == null) {
            Toast.makeText(
                    SettingsActivity.this, "No client key and certificate", Toast.LENGTH_SHORT)
                .show();
            SettingsActivity.this.secureSwitch.setChecked(false);
            settings.setSecure(false);
            return;
          }
          if (settings.getTrustedList().isEmpty()) {
            Toast.makeText(SettingsActivity.this, "No trusted servers", Toast.LENGTH_SHORT).show();
            SettingsActivity.this.secureSwitch.setChecked(false);
            settings.setSecure(false);
            return;
          }
          settings.setSecure(secureSwitch.isChecked());
        });
    this.secureSwitch.setChecked(settings.getSecure());

    editPort.setOnFocusChangeListener(
        (view, focus) -> {
          try {
            if (focus) return;
            String portStr = ((EditText) view).getText().toString();
            int port = Integer.parseInt(portStr);
            if (port <= 0 || 65536 <= port) {
              ((EditText) view).setText(settings.getPort());
              Toast.makeText(SettingsActivity.this, "Invalid port", Toast.LENGTH_SHORT).show();
              return;
            }
            settings.setPort(port);
          } catch (Exception ignored) {
            Toast.makeText(SettingsActivity.this, "Error occurred", Toast.LENGTH_SHORT).show();
          }
        });
    editPortSecure.setOnFocusChangeListener(
        (view, focus) -> {
          try {
            if (focus) return;
            String portStr = ((EditText) view).getText().toString();
            int port = Integer.parseInt(portStr);
            if (port <= 0 || 65536 <= port) {
              ((EditText) view).setText(settings.getPortSecure());
              Toast.makeText(SettingsActivity.this, "Invalid port", Toast.LENGTH_SHORT).show();
              return;
            }
            settings.setPortSecure(port);
          } catch (Exception ignored) {
            Toast.makeText(SettingsActivity.this, "Error occurred", Toast.LENGTH_SHORT).show();
          }
        });
    editPortUDP.setOnFocusChangeListener(
        (view, focus) -> {
          try {
            if (focus) return;
            String portStr = ((EditText) view).getText().toString();
            int port = Integer.parseInt(portStr);
            if (port <= 0 || 65536 <= port) {
              ((EditText) view).setText(settings.getPort());
              Toast.makeText(SettingsActivity.this, "Invalid port", Toast.LENGTH_SHORT).show();
              return;
            }
            settings.setPortUDP(port);
          } catch (Exception ignored) {
            Toast.makeText(SettingsActivity.this, "Error occurred", Toast.LENGTH_SHORT).show();
          }
        });

    editPort.setText(String.valueOf(settings.getPort()));
    editPortSecure.setText(String.valueOf(settings.getPortSecure()));
    editPortUDP.setText(String.valueOf(settings.getPortUDP()));
    this.caCnTxt.setMovementMethod(new ScrollingMovementMethod());
    this.caCnTxt.setHorizontallyScrolling(true);
    String caCertCN = settings.getCACertCN();
    if (caCertCN != null) this.caCnTxt.setText(caCertCN);

    this.cnTxt.setMovementMethod(new ScrollingMovementMethod());
    this.cnTxt.setHorizontallyScrolling(true);
    String certCN = settings.getCertCN();
    if (certCN != null) this.cnTxt.setText(certCN);

    for (String server : settings.getTrustedList()) {
      addRowToTrustList(false, server);
    }

    for (String server : settings.getAutoSendTrustedList()) {
      addRowToAutoSendTrustList(false, server);
    }

    autoSendTextSwitch.setOnClickListener(
        view -> settings.setAutoSendText(autoSendTextSwitch.isChecked()));
    autoSendTextSwitch.setChecked(settings.getAutoSendText());

    autoSendFileSwitch.setOnClickListener(
        view -> settings.setAutoSendFiles(autoSendFileSwitch.isChecked()));
    autoSendFileSwitch.setChecked(settings.getAutoSendFiles());

    addAutoSendServerBtn.setOnClickListener(view -> addRowToAutoSendTrustList(true, null));

    vibrateSwitch.setOnClickListener(view -> settings.setVibrate(vibrateSwitch.isChecked()));
    vibrateSwitch.setChecked(settings.getVibrate());

    autoCloseSwitch.setOnClickListener(
        view -> {
          boolean autoClose = autoCloseSwitch.isChecked();
          settings.setCloseIfIdle(autoClose);
          if (autoClose) {
            layoutAutoCloseDelay.setVisibility(View.VISIBLE);
          } else {
            layoutAutoCloseDelay.setVisibility(View.GONE);
          }
        });
    autoCloseSwitch.setChecked(settings.getCloseIfIdle());

    if (!settings.getCloseIfIdle()) {
      layoutAutoCloseDelay.setVisibility(View.GONE);
    } else {
      layoutAutoCloseDelay.setVisibility(View.VISIBLE);
    }

    editAutoCloseDelay.setOnFocusChangeListener(
        (view, focus) -> {
          try {
            if (focus) return;
            String delayStr = ((EditText) view).getText().toString();
            int delay = Integer.parseInt(delayStr);
            if (delay <= 0 || 10000 <= delay) {
              ((EditText) view).setText(settings.getAutoCloseDelay());
              Toast.makeText(SettingsActivity.this, "Invalid delay", Toast.LENGTH_SHORT).show();
              return;
            }
            settings.setAutoCloseDelay(delay);
          } catch (Exception ignored) {
            Toast.makeText(SettingsActivity.this, "Error occurred", Toast.LENGTH_SHORT).show();
          }
        });
    editAutoCloseDelay.setText(String.valueOf(settings.getAutoCloseDelay()));

    SwitchCompat saveAddressesSwitch = findViewById(R.id.saveAddressesSwitch);
    saveAddressesSwitch.setOnClickListener(
        view -> settings.setSaveServers(saveAddressesSwitch.isChecked()));
    saveAddressesSwitch.setChecked(settings.getSaveServers());

    addTrustedCNBtn.setOnClickListener(view -> addRowToTrustList(true, null));

    caBrowseBtn.setOnClickListener(
        view -> {
          Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
          intent.setType("*/*");
          String[] mimeTypes = {
            "application/x-x509-ca-cert",
            "application/x-pem-file",
            "application/pkix-cert+pem",
            "application/pkix-cert"
          };
          intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
          intent.addCategory(Intent.CATEGORY_OPENABLE);
          caActivityLauncher.launch(intent);
        });

    clientBrowseBtn.setOnClickListener(
        view -> {
          Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
          intent.setType("application/x-pkcs12");
          intent.addCategory(Intent.CATEGORY_OPENABLE);
          clientActivityLauncher.launch(intent);
        });

    getOnBackPressedDispatcher()
        .addCallback(
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                try {
                  SharedPreferences sharedPref =
                      getApplicationContext()
                          .getSharedPreferences(
                              ClipShareActivity.PREFERENCES, Context.MODE_PRIVATE);
                  SharedPreferences.Editor editor = sharedPref.edit();
                  editor.putString("settings", settings.toString());
                  editor.apply();
                } catch (Exception ignored) {
                }
                if (SettingsActivity.this.intent != null) {
                  SettingsActivity.this.setResult(Activity.RESULT_OK, intent);
                }
                SettingsActivity.this.finish();
              }
            });

    Button exportBtn = findViewById(R.id.btnExport);
    exportBtn.setOnClickListener(
        view -> {
          try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "ClipShare_settings.json");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
              Uri pickerInitialUri =
                  Uri.fromFile(
                      Environment.getExternalStoragePublicDirectory(
                          Environment.DIRECTORY_DOCUMENTS));
              intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri);
            }
            exportActivityLauncher.launch(intent);
          } catch (Exception ignored) {
          }
        });

    Button importBtn = findViewById(R.id.btnImport);
    importBtn.setOnClickListener(
        view -> {
          try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            String[] mimeTypes = {"application/json", "application/octet-stream"};
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            importActivityLauncher.launch(intent);
          } catch (Exception ignored) {
          }
        });
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent event) {
    if (event.getAction() == MotionEvent.ACTION_DOWN) {
      View v = getCurrentFocus();
      if (v instanceof EditText) {
        Rect outRect = new Rect();
        v.getGlobalVisibleRect(outRect);
        if (!outRect.contains((int) event.getRawX(), (int) event.getRawY())) {
          v.clearFocus();
          InputMethodManager imm =
              (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
          imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
      }
    }
    return super.dispatchTouchEvent(event);
  }
}
