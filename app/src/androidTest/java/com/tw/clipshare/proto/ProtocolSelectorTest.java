package com.tw.clipshare.proto;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.tw.clipshare.netConnection.MockConnection;
import com.tw.clipshare.protocol.Proto;
import com.tw.clipshare.protocol.Proto_v3;
import com.tw.clipshare.protocol.ProtocolSelector;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.ProtocolException;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ProtocolSelectorTest {
  static final byte MAX_PROTO = 3;

  @Test
  public void testNullConnection() throws IOException {
    Proto proto = ProtocolSelector.getProto(null, null, null);
    assertNull(proto);
  }

  @Test
  public void testProtoOk() throws IOException {
    BAOStreamBuilder builder = new BAOStreamBuilder();
    builder.addByte(1);
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);
    Proto proto = ProtocolSelector.getProto(connection, null, null);
    assertTrue(proto instanceof Proto_v3);
    byte[] received = connection.getOutputBytes();
    assertArrayEquals(new byte[] {MAX_PROTO}, received);
    proto.close();
  }

  @Test
  public void testProtoObsolete() {
    BAOStreamBuilder builder = new BAOStreamBuilder();
    builder.addByte(2);
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);
    assertThrows(ProtocolException.class, () -> ProtocolSelector.getProto(connection, null, null));
    byte[] received = connection.getOutputBytes();
    assertArrayEquals(new byte[] {MAX_PROTO}, received);
  }

  @Test
  public void testProtoNegotiateV1() throws ProtocolException {
    BAOStreamBuilder builder = new BAOStreamBuilder();
    builder.addByte(3);
    builder.addByte(1);
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);
    Proto proto = ProtocolSelector.getProto(connection, null, null);
    byte[] received = connection.getOutputBytes();
    assertArrayEquals(new byte[] {MAX_PROTO, 1}, received);
    proto.close();
  }

  @Test
  public void testProtoNegotiateFail() {
    BAOStreamBuilder builder = new BAOStreamBuilder();
    builder.addByte(3);
    builder.addByte(0);
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);
    assertThrows(ProtocolException.class, () -> ProtocolSelector.getProto(connection, null, null));
    byte[] received = connection.getOutputBytes();
    assertArrayEquals(new byte[] {MAX_PROTO, 0}, received);
  }

  @Test
  public void testInvalidStatus() throws ProtocolException {
    BAOStreamBuilder builder = new BAOStreamBuilder();
    builder.addByte(4);
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);
    Proto proto = ProtocolSelector.getProto(connection, null, null);
    assertNull(proto);
  }

  @Test
  public void testReceiveFail1() throws ProtocolException {
    BAOStreamBuilder builder = new BAOStreamBuilder();
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);
    Proto proto = ProtocolSelector.getProto(connection, null, null);
    assertNull(proto);
  }

  @Test
  public void testReceiveFail2() throws ProtocolException {
    BAOStreamBuilder builder = new BAOStreamBuilder();
    builder.addByte(3);
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);
    Proto proto = ProtocolSelector.getProto(connection, null, null);
    assertNull(proto);
  }
}
