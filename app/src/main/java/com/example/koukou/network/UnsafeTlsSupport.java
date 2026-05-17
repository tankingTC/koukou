package com.example.koukou.network;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;

public final class UnsafeTlsSupport {
    private static final X509TrustManager TRUST_ALL_MANAGER = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    };

    private static final SSLSocketFactory UNSAFE_SSL_SOCKET_FACTORY = createUnsafeSocketFactory();
    private static final HostnameVerifier TRUST_ALL_HOSTS = new HostnameVerifier() {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    };

    private UnsafeTlsSupport() {
    }

    public static OkHttpClient.Builder apply(OkHttpClient.Builder builder) {
        return builder
                .sslSocketFactory(UNSAFE_SSL_SOCKET_FACTORY, TRUST_ALL_MANAGER)
                .hostnameVerifier(TRUST_ALL_HOSTS);
    }

    private static SSLSocketFactory createUnsafeSocketFactory() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{TRUST_ALL_MANAGER}, new SecureRandom());
            return sslContext.getSocketFactory();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create unsafe TLS socket factory", e);
        }
    }
}
