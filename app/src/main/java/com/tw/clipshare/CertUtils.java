package com.tw.clipshare;

import javax.net.ssl.KeyManagerFactory;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

public class CertUtils {
    public static String getCertCN(X509Certificate cert) {
        try {
            String name = cert.getSubjectX500Principal().getName("RFC1779");
            String[] attributes = name.split(",");
            String cn = null;
            for (String attribute : attributes) {
                if (!attribute.startsWith("CN=")) {
                    continue;
                }
                String[] cnSep = attribute.split("=", 2);
                cn = cnSep[1];
                break;
            }
            return cn;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String getCertCN(char[] passwd, InputStream certIn) {
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(certIn, passwd);
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, passwd);
            Enumeration<String> enm = keyStore.aliases();
            if (enm.hasMoreElements()) {
                String alias = enm.nextElement();
                X509Certificate cert = (X509Certificate) keyStore.getCertificate(alias);
                return getCertCN(cert);
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static X509Certificate getX509fromInputStream(InputStream caCertIn) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(caCertIn);
        } catch (Exception ignored) {
            return null;
        }
    }
}
