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

package com.tw.clipshare.netConnection;

import com.tw.clipshare.CertUtils;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.*;

public class SecureConnection extends SocketConnection {

  private static final Object CTX_LOCK = new Object();
  private static SSLContext ctxInstance = null;

  /**
   * TLS encrypted connection to the server.
   *
   * @param serverAddress address of the server
   * @param port port on which the server is listening
   * @param caCertInput input stream to get the CA's certificate
   * @param clientCertStoreInput input stream to get the client key certificate store
   * @param certStorePassword input stream to get the client key certificate store password
   * @param acceptedCNs array of accepted servers (common names)
   * @throws IOException on connection error
   * @throws GeneralSecurityException on security related errors
   */
  public SecureConnection(
      InetAddress serverAddress,
      int port,
      InputStream caCertInput,
      InputStream clientCertStoreInput,
      char[] certStorePassword,
      String[] acceptedCNs)
      throws IOException, GeneralSecurityException {
    SSLContext ctx = getCtxInstance(caCertInput, clientCertStoreInput, certStorePassword);
    SSLSocketFactory sslsocketfactory = ctx.getSocketFactory();
    SSLSocket sslsocket = (SSLSocket) sslsocketfactory.createSocket(serverAddress, port);
    SSLSession sslSession = sslsocket.getSession();
    X509Certificate serverCertificate = (X509Certificate) sslSession.getPeerCertificates()[0];
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

  public SecureConnection(SSLSocket sslSocket) throws IOException {
    this.socket = sslSocket;
    this.inStream = this.socket.getInputStream();
    this.outStream = this.socket.getOutputStream();
  }

  private static SSLContext getCtxInstance(
      InputStream caCertInput, InputStream clientCertStoreInput, char[] certStorePassword)
      throws GeneralSecurityException, IOException {
    synchronized (SecureConnection.CTX_LOCK) {
      if (SecureConnection.ctxInstance == null) {
        X509Certificate caCert = CertUtils.getX509fromInputStream(caCertInput);
        TrustManagerFactory tmf =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null);
        ks.setCertificateEntry("caCert", caCert);
        tmf.init(ks);

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(clientCertStoreInput, certStorePassword);
        KeyManagerFactory kmf =
            KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, certStorePassword);

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        SecureConnection.ctxInstance = ctx;
        return ctx;
      } else {
        return SecureConnection.ctxInstance;
      }
    }
  }

  public static ServerSocket getSecureServerSocket(
      int port, InputStream caCertInput, InputStream clientCertStoreInput, char[] certStorePassword)
      throws GeneralSecurityException, IOException {
    SSLContext ctx = getCtxInstance(caCertInput, clientCertStoreInput, certStorePassword);
    SSLServerSocket serverSocket =
        (SSLServerSocket) ctx.getServerSocketFactory().createServerSocket(port, 3);
    serverSocket.setNeedClientAuth(true);
    return serverSocket;
  }

  /** Reset the SSLContext instance to null */
  public static void resetSSLContext() {
    ExecutorService executorService = Executors.newSingleThreadExecutor();
    Runnable resetCtx =
        () -> {
          try {
            synchronized (SecureConnection.CTX_LOCK) {
              SecureConnection.ctxInstance = null;
            }
          } catch (Exception ignored) {
          }
        };
    executorService.submit(resetCtx);
  }
}
