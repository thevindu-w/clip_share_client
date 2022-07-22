package com.tw.clipshare.netConnection;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class SecureConnection extends ServerConnection {

    private static final int PORT = 4338;

    public SecureConnection(InetAddress serverAddr, InputStream caCertInput, InputStream clientCertStoreInput, char[] certStorePassword, String[] acceptedCNs) throws IOException, GeneralSecurityException {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate caCert = (X509Certificate) cf.generateCertificate(caCertInput);
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
            String name = serverCertificate.getSubjectX500Principal().getName("RFC1779");
            String[] attributes = name.split(",");
            String cn = "";
            for (String attribute : attributes) {
                if (!attribute.startsWith("CN=")) {
                    continue;
                }
                String[] cnSep = attribute.split("=", 2);
                cn = cnSep[1];
                break;
            }
            for (String acceptedCN : acceptedCNs) {
                if (acceptedCN.equals(cn)) {
                    accepted = true;
                    break;
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
