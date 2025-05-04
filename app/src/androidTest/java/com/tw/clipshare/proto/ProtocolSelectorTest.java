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

package com.tw.clipshare.proto;

import static com.tw.clipshare.Utils.PROTOCOL_OBSOLETE;
import static com.tw.clipshare.Utils.PROTOCOL_SUPPORTED;
import static com.tw.clipshare.Utils.PROTOCOL_UNKNOWN;
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
          protoClass = ProtoV1.class;
          break;
        }
      case 2:
        {
          protoClass = ProtoV2.class;
          break;
        }
      case 3:
        {
          protoClass = ProtoV3.class;
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
