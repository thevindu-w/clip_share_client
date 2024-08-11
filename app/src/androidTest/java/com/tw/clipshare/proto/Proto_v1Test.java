package com.tw.clipshare.proto;

import static com.tw.clipshare.proto.ProtocolSelectorTest.MAX_PROTO;
import static com.tw.clipshare.proto.ProtocolSelectorTest.PROTOCOL_UNKNOWN;
import static org.junit.Assert.*;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import androidx.core.app.NotificationCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import com.tw.clipshare.ClipShareActivity;
import com.tw.clipshare.PendingFile;
import com.tw.clipshare.R;
import com.tw.clipshare.netConnection.MockConnection;
import com.tw.clipshare.platformUtils.AndroidStatusNotifier;
import com.tw.clipshare.platformUtils.FSUtils;
import com.tw.clipshare.platformUtils.StatusNotifier;
import com.tw.clipshare.protocol.Proto;
import com.tw.clipshare.protocol.ProtocolSelector;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Random;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class Proto_v1Test {
  @Rule
  public GrantPermissionRule mRuntimePermissionRule =
      GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE);

  private static Context context;
  private static Activity activity;
  private StatusNotifier notifier;

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
              Proto_v1Test.activity = activity;
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
        new NotificationCompat.Builder(context, "Test")
            .setSmallIcon(R.drawable.ic_upload_icon)
            .setContentTitle("Sending files");
    Random rnd = new Random();
    int notificationId = Math.abs(rnd.nextInt(Integer.MAX_VALUE - 1)) + 1;
    this.notifier = new AndroidStatusNotifier(notificationManager, builder, notificationId);
    assertNotNull(this.notifier);
  }

  private BAOStreamBuilder initProto(boolean methodOk) {
    BAOStreamBuilder builder = new BAOStreamBuilder();
    builder.addByte(PROTOCOL_UNKNOWN);
    builder.addByte(1);
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
    PendingFile pendingFile = new PendingFile(new ByteArrayInputStream(new byte[1]), "name", 0);
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
    builder.addByte(1);
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
    Proto proto = ProtocolSelector.getProto(connection, utils, null);
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
    byte[][] files = new byte[2][];
    files[0] = new byte[] {'a', 'b', 'c'};
    files[1] = new byte[] {'1', '2'};
    BAOStreamBuilder builder = initProto(true);
    builder.addSize(files.length);
    for (int i = 0; i < files.length; i++) {
      byte[] file = files[i];
      builder.addString("name " + i);
      builder.addData(file);
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
    String fileName = "file.txt";
    byte[] file = {'a', 'b', 'c'};
    BAOStreamBuilder builder = initProto(true);
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);

    ByteArrayInputStream fileStream = new ByteArrayInputStream(file);
    PendingFile pendingFile = new PendingFile(fileStream, fileName, file.length);
    LinkedList<PendingFile> files = new LinkedList<>();
    files.push(pendingFile);
    FSUtils utils = new FSUtils(context, activity, files);
    Proto proto = ProtocolSelector.getProto(connection, utils, notifier);
    assertTrue(proto.sendFile());
    proto.close();

    builder = new BAOStreamBuilder();
    builder.addByte(MAX_PROTO);
    builder.addByte(1);
    builder.addByte(4);
    builder.addString(fileName);
    builder.addData(file);
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
    assertArrayEquals(new byte[] {MAX_PROTO, 1, 125}, connection.getOutputBytes());
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
