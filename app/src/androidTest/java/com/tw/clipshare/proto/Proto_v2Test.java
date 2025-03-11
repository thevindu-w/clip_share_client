package com.tw.clipshare.proto;

import static com.tw.clipshare.Utils.PROTOCOL_UNKNOWN;
import static com.tw.clipshare.proto.ProtocolSelectorTest.MAX_PROTO;
import static org.junit.Assert.*;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.net.Uri;
import androidx.core.app.NotificationCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import com.tw.clipshare.ClipShareActivity;
import com.tw.clipshare.FileService;
import com.tw.clipshare.PendingFile;
import com.tw.clipshare.R;
import com.tw.clipshare.netConnection.MockConnection;
import com.tw.clipshare.platformUtils.FSUtils;
import com.tw.clipshare.platformUtils.StatusNotifier;
import com.tw.clipshare.protocol.Proto;
import com.tw.clipshare.protocol.ProtocolSelector;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Random;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class Proto_v2Test {
  @Rule
  public GrantPermissionRule mRuntimePermissionRule =
      GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE);

  private static Context context;
  private static Activity activity;
  private StatusNotifier notifier;

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @BeforeClass
  public static void initialize() throws InterruptedException {
    context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    assertNotNull(context);

    Object lock = new Object();
    try (ActivityScenario<ClipShareActivity> scenario =
        ActivityScenario.launch(ClipShareActivity.class)) {
      scenario.onActivity(
          activity -> {
            synchronized (lock) {
              Proto_v2Test.activity = activity;
              lock.notifyAll();
            }
          });
    }
    synchronized (lock) {
      if (activity == null) lock.wait(10000);
    }
    assertNotNull(activity);
  }

  @Before
  public void setNotifier() {
    NotificationManager notificationManager =
        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    NotificationCompat.Builder builder =
        new NotificationCompat.Builder(context, FileService.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_upload_icon)
            .setContentTitle("Sending files");
    Random rnd = new Random();
    int notificationId = Math.abs(rnd.nextInt(Integer.MAX_VALUE - 1)) + 1;
    this.notifier = new StatusNotifier(notificationManager, builder, notificationId);
    assertNotNull(this.notifier);
  }

  private BAOStreamBuilder initProto(boolean methodOk) {
    BAOStreamBuilder builder = new BAOStreamBuilder();
    builder.addByte(PROTOCOL_UNKNOWN);
    builder.addByte(2);
    if (methodOk) builder.addByte(1);
    return builder;
  }

  @Test
  public void testInvalidMethod() throws IOException {
    BAOStreamBuilder builder = initProto(false);
    builder.addByte(4);
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection;
    Proto proto;
    PendingFile pendingFile = new PendingFile(null, "name", 0);
    LinkedList<PendingFile> files = new LinkedList<>();
    files.push(pendingFile);
    FSUtils fsUtils = new FSUtils(context, activity, files);

    connection = new MockConnection(istream);
    proto = ProtocolSelector.getProto(connection, null, null);
    assertFalse(proto.getText());
    assertNotNull(proto.dataContainer);
    assertNull(proto.dataContainer.getString());
    istream.reset();

    connection = new MockConnection(istream);
    proto = ProtocolSelector.getProto(connection, null, null);
    assertFalse(proto.sendText("."));
    istream.reset();

    connection = new MockConnection(istream);
    proto = ProtocolSelector.getProto(connection, fsUtils, this.notifier);
    assertFalse(proto.getFile());
    istream.reset();

    connection = new MockConnection(istream);
    proto = ProtocolSelector.getProto(connection, fsUtils, this.notifier);
    assertFalse(proto.sendFile());
    istream.reset();

    connection = new MockConnection(istream);
    proto = ProtocolSelector.getProto(connection, fsUtils, this.notifier);
    assertFalse(proto.getImage());
    istream.reset();

    connection = new MockConnection(istream);
    proto = ProtocolSelector.getProto(connection, null, null);
    assertNull(proto.checkInfo());
    istream.reset();
  }

  @Test
  public void testGetText() throws IOException {
    String sample = "This is a sample text\nLine 2";
    BAOStreamBuilder builder = initProto(true);
    builder.addString(sample);
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);
    Proto proto = ProtocolSelector.getProto(connection, null, null);
    assertTrue(proto.getText());
    assertNotNull(proto.dataContainer);
    assertEquals(sample, proto.dataContainer.getString());
    proto.close();
  }

  @Test
  public void testGetTextNoData() throws IOException {
    BAOStreamBuilder builder = initProto(false);
    builder.addByte(2);
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);
    Proto proto = ProtocolSelector.getProto(connection, null, null);
    assertFalse(proto.getText());
    assertNotNull(proto.dataContainer);
    assertNull(proto.dataContainer.getString());
    proto.close();
  }

  @Test
  public void testGetTextTooLong() throws IOException {
    BAOStreamBuilder builder = initProto(true);
    builder.addSize(4294967296L);
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);
    Proto proto = ProtocolSelector.getProto(connection, null, null);
    assertFalse(proto.getText());
    assertNotNull(proto.dataContainer);
    assertNull(proto.dataContainer.getString());
    proto.close();
  }

  @Test
  public void testGetTextNegativeLen() throws IOException {
    BAOStreamBuilder builder = initProto(true);
    builder.addSize(-1);
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);
    Proto proto = ProtocolSelector.getProto(connection, null, null);
    assertFalse(proto.getText());
    assertNotNull(proto.dataContainer);
    assertNull(proto.dataContainer.getString());
    proto.close();
  }

  @Test
  public void testGetTextLengthReadFailure() throws IOException {
    BAOStreamBuilder builder = initProto(true);
    builder.addBytes(new byte[] {0, 0, 0, 0, 0, 0, 1});
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);
    Proto proto = ProtocolSelector.getProto(connection, null, null);
    assertFalse(proto.getText());
    assertNotNull(proto.dataContainer);
    assertNull(proto.dataContainer.getString());
    proto.close();
  }

  @Test
  public void testGetTextLengthMismatch() throws IOException {
    BAOStreamBuilder builder = initProto(true);
    builder.addSize(2);
    builder.addByte('a');
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);
    Proto proto = ProtocolSelector.getProto(connection, null, null);
    assertFalse(proto.getText());
    assertNotNull(proto.dataContainer);
    assertNull(proto.dataContainer.getString());
    proto.close();
  }

  @Test
  public void testSendText() throws IOException {
    String sample = "This is a sample text\nLine 2";
    BAOStreamBuilder builder = initProto(true);
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);
    Proto proto = ProtocolSelector.getProto(connection, null, null);
    assertTrue(proto.sendText(sample));
    byte[] receivedBytes = connection.getOutputBytes();
    proto.close();

    builder = new BAOStreamBuilder();
    builder.addByte(MAX_PROTO);
    builder.addByte(2);
    builder.addByte(2);
    builder.addString(sample);
    byte[] expected = builder.getArray();
    assertArrayEquals(expected, receivedBytes);
    assertEquals(-1, istream.read());
  }

  @Test
  public void testSendTextNull() throws IOException {
    BAOStreamBuilder builder = initProto(true);
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);
    Proto proto = ProtocolSelector.getProto(connection, null, null);
    assertFalse(proto.sendText(null));
    proto.close();
  }

  @Test
  public void testGetImage() throws IOException {
    byte[] png = {(byte) 137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0};
    BAOStreamBuilder builder = initProto(true);
    builder.addData(png);
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);
    FSUtils utils = new FSUtils(context, activity);
    Proto proto = ProtocolSelector.getProto(connection, utils, this.notifier);
    assertTrue(proto.getImage());
    proto.close();
  }

  @Test
  public void testGetImageNullUtils() throws IOException {
    BAOStreamBuilder builder = initProto(true);
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);
    Proto proto = ProtocolSelector.getProto(connection, null, null);
    assertFalse(proto.getImage());
    proto.close();
  }

  @Test
  public void testGetFile() throws IOException {
    byte[][] files = new byte[3][];
    String[] fileNames = {"a.txt", "dir/b.txt", "dir/sub/c.txt"};
    files[0] = new byte[] {'a', 'b', 'c'};
    files[1] = new byte[] {'1', '2'};
    files[2] = new byte[] {};
    BAOStreamBuilder builder = initProto(true);
    builder.addSize(files.length);
    for (int i = 0; i < files.length; i++) {
      builder.addString(fileNames[i]);
      builder.addData(files[i]);
    }
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);
    FSUtils utils = new FSUtils(context, activity);
    Proto proto = ProtocolSelector.getProto(connection, utils, this.notifier);
    assertTrue(proto.getFile());
    proto.close();
  }

  @Test
  public void testGetFileNoData() throws IOException {
    BAOStreamBuilder builder = initProto(false);
    builder.addByte(2);
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);
    FSUtils utils = new FSUtils(context, activity);
    Proto proto = ProtocolSelector.getProto(connection, utils, this.notifier);
    assertFalse(proto.getFile());
    proto.close();
  }

  @Test
  public void testSendFile() throws IOException {
    byte[][] fileContents = new byte[3][];
    String[] fileNames = {"a.txt", "dir/b.txt", "dir/sub/c.txt"};
    fileContents[0] = new byte[] {'a', 'b', 'c'};
    fileContents[1] = new byte[] {'1', '2'};
    fileContents[2] = new byte[] {};
    BAOStreamBuilder builder = initProto(true);
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);

    LinkedList<PendingFile> pendingFiles = new LinkedList<>();
    for (int i = 0; i < fileContents.length; i++) {
      byte[] file = fileContents[i];
      long size = file.length;
      if (i == 1) size = -1;
      String fileName = fileNames[i];
      if (fileName.contains("/"))
        temporaryFolder.newFolder(fileName.substring(0, fileName.lastIndexOf('/')));
      File tmpFile = temporaryFolder.newFile(fileName);
      FileOutputStream fileOutputStream = new FileOutputStream(tmpFile);
      fileOutputStream.write(fileContents[i]);
      fileOutputStream.close();
      Uri uri = Uri.fromFile(tmpFile);
      PendingFile pendingFile = new PendingFile(uri, fileName, size);
      pendingFiles.add(pendingFile);
    }
    FSUtils utils = new FSUtils(context, activity, pendingFiles);
    Proto proto = ProtocolSelector.getProto(connection, utils, this.notifier);
    assertTrue(proto.sendFile());
    proto.close();

    builder = new BAOStreamBuilder();
    builder.addByte(MAX_PROTO);
    builder.addByte(2);
    builder.addByte(4);
    builder.addSize(fileContents.length);
    for (int i = 0; i < fileContents.length; i++) {
      builder.addString(fileNames[i]);
      builder.addData(fileContents[i]);
    }
    byte[] expected = builder.getArray();
    assertArrayEquals(expected, connection.getOutputBytes());
  }

  @Test
  public void testCheckInfo() throws IOException {
    String info = "ClipShare";
    BAOStreamBuilder builder = initProto(true);
    builder.addString(info);
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);
    Proto proto = ProtocolSelector.getProto(connection, null, null);
    assertEquals(info, proto.checkInfo());
    assertArrayEquals(new byte[] {MAX_PROTO, 2, 125}, connection.getOutputBytes());
    proto.close();
  }

  @Test
  public void testCheckInfoEmpty() throws IOException {
    String info = "";
    BAOStreamBuilder builder = initProto(true);
    builder.addString(info);
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);
    Proto proto = ProtocolSelector.getProto(connection, null, null);
    assertNull(proto.checkInfo());
  }

  @Test
  public void testCheckInfoNull() throws IOException {
    BAOStreamBuilder builder = initProto(true);
    builder.addSize(-1);
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);
    Proto proto = ProtocolSelector.getProto(connection, null, null);
    assertNull(proto.checkInfo());
  }
}
