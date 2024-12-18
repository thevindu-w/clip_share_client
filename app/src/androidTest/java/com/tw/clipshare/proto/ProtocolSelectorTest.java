package com.tw.clipshare.proto;

import static com.tw.clipshare.Consts.PROTOCOL_OBSOLETE;
import static com.tw.clipshare.Consts.PROTOCOL_SUPPORTED;
import static com.tw.clipshare.Consts.PROTOCOL_UNKNOWN;
import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.tw.clipshare.netConnection.MockConnection;
import com.tw.clipshare.protocol.*;
import com.tw.clipshare.protocol.ProtocolSelector;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.ProtocolException;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ProtocolSelectorTest {
  static final byte PROTOCOL_REJECT = 0;
  static final byte MAX_PROTO = ProtocolSelector.PROTO_MAX;

  @SuppressWarnings("ConstantConditions")
  @Test
  public void testNullConnection() throws IOException {
    Proto proto = ProtocolSelector.getProto(null, null, null);
    assertNull(proto);
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  public void testProtoOk() throws IOException {
    BAOStreamBuilder builder = new BAOStreamBuilder();
    builder.addByte(PROTOCOL_SUPPORTED);
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);
    Proto proto = ProtocolSelector.getProto(connection, null, null);
    Class<?> protoClass;
    switch (MAX_PROTO) {
      case 1:
        {
          protoClass = Proto_v1.class;
          break;
        }
      case 2:
        {
          protoClass = Proto_v2.class;
          break;
        }
      case 3:
        {
          protoClass = Proto_v3.class;
          break;
        }
      default:
        {
          throw new ProtocolException("Unknown protocol version");
        }
    }
    assertTrue(protoClass.isInstance(proto));
    byte[] received = connection.getOutputBytes();
    assertArrayEquals(new byte[] {MAX_PROTO}, received);
    proto.close();
  }

  @Test
  public void testProtoObsolete() {
    BAOStreamBuilder builder = new BAOStreamBuilder();
    builder.addByte(PROTOCOL_OBSOLETE);
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);
    assertThrows(ProtocolException.class, () -> ProtocolSelector.getProto(connection, null, null));
    byte[] received = connection.getOutputBytes();
    assertArrayEquals(new byte[] {MAX_PROTO}, received);
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  public void testProtoNegotiateV1() throws ProtocolException {
    if (MAX_PROTO <= 1) return;
    BAOStreamBuilder builder = new BAOStreamBuilder();
    builder.addByte(PROTOCOL_UNKNOWN);
    builder.addByte(1);
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);
    Proto proto = ProtocolSelector.getProto(connection, null, null);
    byte[] received = connection.getOutputBytes();
    assertArrayEquals(new byte[] {MAX_PROTO, 1}, received);
    proto.close();
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  public void testProtoNegotiateV2() throws ProtocolException {
    if (MAX_PROTO <= 2) return;
    BAOStreamBuilder builder = new BAOStreamBuilder();
    builder.addByte(PROTOCOL_UNKNOWN);
    builder.addByte(2);
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);
    Proto proto = ProtocolSelector.getProto(connection, null, null);
    byte[] received = connection.getOutputBytes();
    assertArrayEquals(new byte[] {MAX_PROTO, 2}, received);
    proto.close();
  }

  @Test
  public void testProtoNegotiateFail() {
    BAOStreamBuilder builder = new BAOStreamBuilder();
    builder.addByte(PROTOCOL_UNKNOWN);
    builder.addByte(PROTOCOL_REJECT);
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);
    assertThrows(ProtocolException.class, () -> ProtocolSelector.getProto(connection, null, null));
    byte[] received = connection.getOutputBytes();
    assertArrayEquals(new byte[] {MAX_PROTO, 0}, received);
  }

  @Test
  public void testInvalidStatus() throws ProtocolException {
    BAOStreamBuilder builder = new BAOStreamBuilder();
    builder.addByte(4); // 4 is invalid
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
    builder.addByte(PROTOCOL_UNKNOWN);
    ByteArrayInputStream istream = builder.getStream();
    MockConnection connection = new MockConnection(istream);
    Proto proto = ProtocolSelector.getProto(connection, null, null);
    assertNull(proto);
  }
}
