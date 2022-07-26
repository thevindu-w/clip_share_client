package com.tw.clipshare.netConnection;

import com.tw.clipshare.CertUtils;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

public class SecureConnection extends ServerConnection {

    private static final int PORT = 4338;

    public SecureConnection(InetAddress serverAddr, InputStream caCertInput, InputStream clientCertStoreInput, char[] certStorePassword, String[] acceptedCNs) throws IOException, GeneralSecurityException {
        X509Certificate caCert = CertUtils.getX509fromInputStream(caCertInput);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null);
        ks.setCertificateEntry("caCert", caCert);
        tmf.init(ks);

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(clientCertStoreInput, certStorePassword);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, certStorePassword);

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        SSLSocketFactory sslsocketfactory = ctx.getSocketFactory();
        SSLSocket sslsocket = (SSLSocket) sslsocketfactory.createSocket(serverAddr, PORT);
        SSLSession sess = sslsocket.getSession();
        X509Certificate serverCertificate = (X509Certificate) sess.getPeerCertificates()[0];
        boolean accepted = false;
        try {
            String cn = CertUtils.getCertCN(serverCertificate);
            if (cn != null) {
                for (String acceptedCN : acceptedCNs) {
                    if (acceptedCN.equals(cn)) {
                        accepted = true;
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        if (!accepted) {
            throw new SecurityException("Untrusted Server");
        }
        this.socket = sslsocket;
        this.inStream = this.socket.getInputStream();
        this.outStream = this.socket.getOutputStream();
    }
}
