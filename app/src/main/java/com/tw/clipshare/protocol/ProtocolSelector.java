package com.tw.clipshare.protocol;

import com.tw.clipshare.netConnection.ServerConnection;
import com.tw.clipshare.platformUtils.AndroidUtils;
import com.tw.clipshare.platformUtils.StatusNotifier;

public class ProtocolSelector {

    static final byte PROTOCOL_SUPPORTED = 1;
    static final byte PROTOCOL_OBSOLETE = 2;
    static final byte PROTOCOL_UNKNOWN = 3;
    private static final byte PROTO_MAX = 2;

    public static Proto getProto(ServerConnection connection, AndroidUtils utils, StatusNotifier notifier) {
        if (connection == null) {
            return null;
        }
        byte[] proto_v = {PROTO_MAX};
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
            byte serverMaxProto = serverProto[0];
            if (serverMaxProto == 1) {
                proto_v[0] = serverMaxProto;
                if (!connection.send(proto_v)) {
                    return null;
                }
                return new Proto_v1(connection, utils, notifier);
            }
            serverProto[0] = 0;
            connection.send(serverProto);
            return null;
        } else if (proto_v[0] != ProtocolSelector.PROTOCOL_SUPPORTED) {
            return null;
        }
        return new Proto_v2(connection, utils, notifier);
    }
}
