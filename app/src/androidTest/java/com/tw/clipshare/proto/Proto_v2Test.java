package com.tw.clipshare.proto;

import static com.tw.clipshare.proto.ProtocolSelectorTest.MAX_PROTO;
import static com.tw.clipshare.proto.ProtocolSelectorTest.PROTOCOL_UNKNOWN;
import static org.junit.Assert.*;

import android.Manifest;
import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import com.tw.clipshare.PendingFile;
import com.tw.clipshare.netConnection.MockConnection;
import com.tw.clipshare.platformUtils.FSUtils;
import com.tw.clipshare.protocol.Proto;
import com.tw.clipshare.protocol.ProtocolSelector;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedList;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class Proto_v2Test {
  @Rule
  public GrantPermissionRule mRuntimePermissionRule =
      GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE);

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
    Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    PendingFile pendingFile = new PendingFile(new ByteArrayInputStream(new byte[1]), "name", 0);
    LinkedList<PendingFile> files = new LinkedList<>();
    files.push(pendingFile);
    FSUtils fsUtils = new FSUtils(appContext, null, files);

    connection = new MockConnection(istream);
    proto = ProtocolSelector.getProto(connection, null, null);
    assertNull(proto.getText());
    istream.reset();

    connection = new MockConnection(istream);
    proto = ProtocolSelector.getProto(connection, null, null);
    assertFalse(proto.sendText("."));
    istream.reset();

    connection = new MockConnection(istream);
    proto = ProtocolSelector.getProto(connection, fsUtils, null);
    assertFalse(proto.getFile());
    istream.reset();

    connection = new MockConnection(istream);
    proto = ProtocolSelector.getProto(connection, fsUtils, null);
    assertFalse(proto.sendFile());
    istream.reset();

    connection = new MockConnection(istream);
    proto = ProtocolSelector.getProto(connection, fsUtils, null);
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
    Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    FSUtils utils = new FSUtils(appContext, null);
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
    Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    FSUtils utils = new FSUtils(appContext, null);
    Proto proto = ProtocolSelector.getProto(connection, utils, null);
    assertTrue(proto.getFile());
    proto.close();
  }

  @Test
  public void testGetFileNoData() throws IOException {
    BAOStreamBuilder builder = initProto(false);
    builder.addByte(2);
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);
    Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    FSUtils utils = new FSUtils(appContext, null);
    Proto proto = ProtocolSelector.getProto(connection, utils, null);
    assertFalse(proto.getFile());
    proto.close();
  }

  @Test
  public void testSendFile() throws IOException {
    byte[][] files = new byte[3][];
    String[] fileNames = {"a.txt", "dir/b.txt", "dir/sub/c.txt"};
    files[0] = new byte[] {'a', 'b', 'c'};
    files[1] = new byte[] {'1', '2'};
    files[2] = new byte[] {};
    BAOStreamBuilder builder = initProto(true);
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);

    LinkedList<PendingFile> pendingFiles = new LinkedList<>();
    for (int i = 0; i < files.length; i++) {
      byte[] file = files[i];
      ByteArrayInputStream fileStream = new ByteArrayInputStream(file);
      long size = file.length;
      if (i == 1) size = -1;
      PendingFile pendingFile = new PendingFile(fileStream, fileNames[i], size);
      pendingFiles.add(pendingFile);
    }
    Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    FSUtils utils = new FSUtils(appContext, null, pendingFiles);
    Proto proto = ProtocolSelector.getProto(connection, utils, null);
    assertTrue(proto.sendFile());
    proto.close();

    builder = new BAOStreamBuilder();
    builder.addByte(MAX_PROTO);
    builder.addByte(2);
    builder.addByte(4);
    builder.addSize(files.length);
    for (int i = 0; i < files.length; i++) {
      builder.addString(fileNames[i]);
      builder.addData(files[i]);
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
