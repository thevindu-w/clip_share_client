package com.tw.clipshare.protocol;

import com.tw.clipshare.netConnection.ServerConnection;
import com.tw.clipshare.platformUtils.AndroidUtils;
import com.tw.clipshare.platformUtils.StatusNotifier;

public class ProtocolSelector {

    static final byte PROTOCOL_SUPPORTED = 1;
    static final byte PROTOCOL_OBSOLETE = 2;
    static final byte PROTOCOL_UNKNOWN = 3;
    private static final byte PROTO_MIN = 1;

    public static Proto_v1 getProto_v1(ServerConnection connection, AndroidUtils utils, StatusNotifier notifier) {
        if (connection == null) {
            return null;
        }
        byte[] proto_v = {1};
        if (!connection.send(proto_v)) {
            return null;
        }
        if (connection.receive(proto_v)) {
            return null;
        }
        if (proto_v[0] == ProtocolSelector.PROTOCOL_OBSOLETE) {
            return null;
        } else if (proto_v[0] == ProtocolSelector.PROTOCOL_UNKNOWN) {
            byte[] serverProto = new byte[1];
            if (connection.receive(serverProto)) {
                return null;
            }
            if (serverProto[0] < PROTO_MIN) {
                serverProto[0] = 0;
                connection.send(serverProto);
                return null;
            }
            // TODO: select appropriate protocol implementation
            return null;
        } else if (proto_v[0] != ProtocolSelector.PROTOCOL_SUPPORTED) {
            return null;
        }
        return new Proto_v1(connection, utils, notifier);
    }
}
