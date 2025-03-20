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

package com.tw.clipshare;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import javax.net.ssl.KeyManagerFactory;

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
      KeyManagerFactory kmf =
          KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
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
