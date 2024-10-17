package com.tw.clipshare.proto;

import static com.tw.clipshare.proto.ProtocolSelectorTest.MAX_PROTO;
import static com.tw.clipshare.proto.ProtocolSelectorTest.PROTOCOL_SUPPORTED;
import static org.junit.Assert.*;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
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
import com.tw.clipshare.platformUtils.AndroidStatusNotifier;
import com.tw.clipshare.platformUtils.FSUtils;
import com.tw.clipshare.platformUtils.StatusNotifier;
import com.tw.clipshare.platformUtils.directoryTree.Directory;
import com.tw.clipshare.platformUtils.directoryTree.RegularFile;
import com.tw.clipshare.protocol.Proto;
import com.tw.clipshare.protocol.Proto_v3;
import com.tw.clipshare.protocol.ProtocolSelector;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.Random;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class Proto_v3Test {
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
              Proto_v3Test.activity = activity;
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
    this.notifier = new AndroidStatusNotifier(notificationManager, builder, notificationId);
    assertNotNull(this.notifier);
  }

  private BAOStreamBuilder initProto(boolean methodOk) {
    BAOStreamBuilder builder = new BAOStreamBuilder();
    builder.addByte(PROTOCOL_SUPPORTED);
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
    assertNull(proto.getText());
    istream.reset();

    connection = new MockConnection(istream);
    proto = ProtocolSelector.getProto(connection, null, null);
    assertFalse(proto.sendText("."));
    istream.reset();

    connection = new MockConnection(istream);
    proto = ProtocolSelector.getProto(connection, fsUtils, notifier);
    assertFalse(proto.getFile());
    istream.reset();

    connection = new MockConnection(istream);
    proto = ProtocolSelector.getProto(connection, fsUtils, notifier);
    assertFalse(proto.sendFile());
    istream.reset();

    connection = new MockConnection(istream);
    proto = ProtocolSelector.getProto(connection, fsUtils, notifier);
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
    assertEquals(sample, proto.getText());
    proto.close();
  }

  @Test
  public void testGetTextNoData() throws IOException {
    BAOStreamBuilder builder = initProto(false);
    builder.addByte(2);
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);
    Proto proto = ProtocolSelector.getProto(connection, null, null);
    assertNull(proto.getText());
    proto.close();
  }

  @Test
  public void testGetTextTooLong() throws IOException {
    BAOStreamBuilder builder = initProto(true);
    builder.addSize(4294967296L);
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);
    Proto proto = ProtocolSelector.getProto(connection, null, null);
    assertNull(proto.getText());
    proto.close();
  }

  @Test
  public void testGetTextNegativeLen() throws IOException {
    BAOStreamBuilder builder = initProto(true);
    builder.addSize(-1);
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);
    Proto proto = ProtocolSelector.getProto(connection, null, null);
    assertNull(proto.getText());
    proto.close();
  }

  @Test
  public void testGetTextLengthReadFailure() throws IOException {
    BAOStreamBuilder builder = initProto(true);
    builder.addBytes(new byte[] {0, 0, 0, 0, 0, 0, 1});
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);
    Proto proto = ProtocolSelector.getProto(connection, null, null);
    assertNull(proto.getText());
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
    assertNull(proto.getText());
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
    Proto proto = ProtocolSelector.getProto(connection, utils, notifier);
    assertTrue(proto.getImage());
    byte[] receivedBytes = connection.getOutputBytes();
    proto.close();

    builder = new BAOStreamBuilder();
    builder.addByte(MAX_PROTO);
    builder.addByte(5);
    byte[] expected = builder.getArray();
    assertArrayEquals(expected, receivedBytes);
    assertEquals(-1, istream.read());
  }

  @Test
  public void testGetCopiedImage() throws IOException {
    byte[] png = {(byte) 137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0};
    BAOStreamBuilder builder = initProto(true);
    builder.addData(png);
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);
    FSUtils utils = new FSUtils(context, activity);
    Proto proto = ProtocolSelector.getProto(connection, utils, notifier);
    if (!(proto instanceof Proto_v3)) fail();
    Proto_v3 protoV3 = (Proto_v3) proto;
    assertTrue(protoV3.getCopiedImage());
    byte[] receivedBytes = connection.getOutputBytes();
    proto.close();

    builder = new BAOStreamBuilder();
    builder.addByte(MAX_PROTO);
    builder.addByte(6);
    byte[] expected = builder.getArray();
    assertArrayEquals(expected, receivedBytes);
    assertEquals(-1, istream.read());
  }

  @Test
  public void testGetScreenshot() throws IOException {
    byte[] png = {(byte) 137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0};
    int display = 1;
    BAOStreamBuilder builder = initProto(true);
    builder.addByte(1);
    builder.addData(png);
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);
    FSUtils utils = new FSUtils(context, activity);
    Proto proto = ProtocolSelector.getProto(connection, utils, notifier);
    if (!(proto instanceof Proto_v3)) fail();
    Proto_v3 protoV3 = (Proto_v3) proto;
    assertTrue(protoV3.getScreenshot(display));
    byte[] receivedBytes = connection.getOutputBytes();
    proto.close();

    builder = new BAOStreamBuilder();
    builder.addByte(MAX_PROTO);
    builder.addByte(7);
    builder.addSize(display);
    byte[] expected = builder.getArray();
    assertArrayEquals(expected, receivedBytes);
    assertEquals(-1, istream.read());
  }

  @Test
  public void testGetScreenshotNoData() throws IOException {
    int display = 2;
    BAOStreamBuilder builder = initProto(true);
    builder.addByte(2);
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);
    FSUtils utils = new FSUtils(context, activity);
    Proto proto = ProtocolSelector.getProto(connection, utils, notifier);
    if (!(proto instanceof Proto_v3)) fail();
    Proto_v3 protoV3 = (Proto_v3) proto;
    assertFalse(protoV3.getScreenshot(display));
    byte[] receivedBytes = connection.getOutputBytes();
    proto.close();

    builder = new BAOStreamBuilder();
    builder.addByte(MAX_PROTO);
    builder.addByte(7);
    builder.addSize(display);
    byte[] expected = builder.getArray();
    assertArrayEquals(expected, receivedBytes);
    assertEquals(-1, istream.read());
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
    String[] dirNames = {"dir2/", "dir3/sub"};
    BAOStreamBuilder builder = initProto(true);
    builder.addSize(files.length);
    for (int i = 0; i < files.length; i++) {
      builder.addString(fileNames[i]);
      builder.addData(files[i]);
    }
    for (String dirName : dirNames) {
      builder.addString(dirName);
      builder.addSize(-1);
    }
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);
    FSUtils utils = new FSUtils(context, activity);
    Proto proto = ProtocolSelector.getProto(connection, utils, notifier);
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
    Proto proto = ProtocolSelector.getProto(connection, utils, notifier);
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
      File tmpFile = temporaryFolder.newFile(fileNames[i]);
      FileOutputStream fileOutputStream = new FileOutputStream(tmpFile);
      fileOutputStream.write(fileContents[i]);
      fileOutputStream.close();
      Uri uri = Uri.fromFile(tmpFile);
      PendingFile pendingFile = new PendingFile(uri, fileNames[i], size);
      pendingFiles.add(pendingFile);
    }
    FSUtils utils = new FSUtils(context, activity, pendingFiles);
    Proto proto = ProtocolSelector.getProto(connection, utils, notifier);
    assertTrue(proto.sendFile());
    proto.close();

    builder = new BAOStreamBuilder();
    builder.addByte(MAX_PROTO);
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
  public void testSendDir() throws IOException {
    BAOStreamBuilder builder = initProto(true);
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);

    Directory root = new Directory("dir", 2, null);
    root.children.add(new Directory("dir2", 0, root));
    Directory dir3 = new Directory("dir3", 1, root);
    root.children.add(dir3);
    dir3.children.add(new Directory("sub", 0, dir3));
    dir3.children.add(new Directory("sub2", 0, dir3));

    String content = "Test";
    String fName = "clip.txt";
    byte[] fileData = content.getBytes(StandardCharsets.UTF_8);
    String fPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + fName;
    File file = new File(fPath);
    FileOutputStream stream = new FileOutputStream(file);
    stream.write(fileData);
    stream.close();

    Uri uri = Uri.fromFile(file);
    dir3.children.add(new RegularFile(fName, fileData.length, uri, dir3));

    FSUtils utils = new FSUtils(context, activity, root);
    Proto proto = ProtocolSelector.getProto(connection, utils, notifier);
    assertTrue(proto.sendFile());
    proto.close();

    String[] dirNames = {"dir/dir2", "dir/dir3/sub", "dir/dir3/sub2"};
    builder = new BAOStreamBuilder();
    builder.addByte(MAX_PROTO);
    builder.addByte(4);
    builder.addSize(dirNames.length + 1);
    for (String dirName : dirNames) {
      builder.addString(dirName);
      builder.addSize(-1);
    }
    builder.addString("dir/dir3/" + fName);
    builder.addData(fileData);

    byte[] expected = builder.getArray();
    byte[] received = connection.getOutputBytes();
    assertArrayEquals(expected, received);
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
    assertArrayEquals(new byte[] {MAX_PROTO, 125}, connection.getOutputBytes());
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
