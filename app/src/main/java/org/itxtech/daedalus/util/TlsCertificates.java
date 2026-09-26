package org.itxtech.daedalus.util;

import android.content.ContentResolver;
import android.net.Uri;
import android.util.Base64;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.server.AbstractDnsServer;
import org.itxtech.daedalus.server.CustomDnsServer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Daedalus Project
 *
 * @author iTX Technologies
 * @link https://itxtech.org
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * Certificates attached to individual custom DNS servers (server settings > TLS
 * certificate) for DNS over TLS servers whose certificate is self-signed or issued by a
 * private CA, e.g. a SmartDNS instance. A server's certificate is trusted, in addition to
 * the system CAs, only for connections to that server; every other server is checked
 * against the system CAs alone.
 * <p>
 * A certificate file (PEM bundle, one file per import) is stored under the app's files
 * directory and referenced by the server through its name, a SHA-256 fingerprint.
 */
public class TlsCertificates {
    private static final String DIRECTORY = "certs";
    private static final String EXTENSION = ".crt";
    private static final String SYSTEM_ONLY = "";

    private static final Map<String, SSLSocketFactory> factories = new HashMap<>();

    public static File getDirectory() {
        File base = Daedalus.getInstance().getExternalFilesDir(null);
        if (base == null) {
            base = Daedalus.getInstance().getFilesDir();
        }
        File directory = new File(base, DIRECTORY);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            Logger.warning("Cannot create certificate directory " + directory);
        }
        return directory;
    }

    public static File getFile(String name) {
        return new File(getDirectory(), name + EXTENSION);
    }

    public static boolean exists(String name) {
        return name != null && getFile(name).isFile();
    }

    /**
     * The stored PEM text of a certificate, for export.
     */
    public static String readPem(String name) throws IOException {
        File file = getFile(name);
        byte[] data = new byte[(int) file.length()];
        try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
            in.readFully(data);
        }
        return new String(data, StandardCharsets.US_ASCII);
    }

    /**
     * The certificates stored under the given name (a self-signed server certificate, or
     * a CA possibly followed by intermediates).
     */
    public static List<X509Certificate> load(String name) throws IOException, CertificateException {
        List<X509Certificate> certificates = new ArrayList<>();
        try (InputStream in = new FileInputStream(getFile(name))) {
            for (Certificate certificate : CertificateFactory.getInstance("X.509").generateCertificates(in)) {
                if (certificate instanceof X509Certificate) {
                    certificates.add((X509Certificate) certificate);
                }
            }
        }
        if (certificates.isEmpty()) {
            throw new CertificateException("No certificate in " + getFile(name));
        }
        return certificates;
    }

    /**
     * Imports the certificate(s) of a document (a PEM file with one or more certificates,
     * or a single DER certificate) and returns the name to store in the server. The name
     * is the SHA-256 fingerprint of the content, so importing a file twice is harmless.
     */
    public static String importFrom(ContentResolver resolver, Uri uri) throws IOException, GeneralSecurityException {
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) {
                throw new IOException("Cannot open " + uri);
            }
            return importFrom(in);
        }
    }

    /**
     * Imports the certificate(s) of a PEM text, e.g. from an exported server list.
     */
    public static String importPem(String pem) throws IOException, GeneralSecurityException {
        return importFrom(new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
    }

    public static String importFrom(InputStream in) throws IOException, GeneralSecurityException {
        Collection<? extends Certificate> parsed = CertificateFactory.getInstance("X.509").generateCertificates(in);
        List<X509Certificate> certificates = new ArrayList<>();
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        for (Certificate certificate : parsed) {
            if (certificate instanceof X509Certificate) {
                certificates.add((X509Certificate) certificate);
                encoded.write(certificate.getEncoded());
            }
        }
        if (certificates.isEmpty()) {
            throw new CertificateException("No certificate found");
        }
        String name = fingerprint(encoded.toByteArray());
        try (Writer out = new OutputStreamWriter(new FileOutputStream(getFile(name)), StandardCharsets.US_ASCII)) {
            for (X509Certificate certificate : certificates) {
                out.write("-----BEGIN CERTIFICATE-----\n");
                out.write(Base64.encodeToString(certificate.getEncoded(), Base64.DEFAULT));
                out.write("-----END CERTIFICATE-----\n");
            }
        }
        invalidate(name);
        return name;
    }

    /**
     * Deletes the certificate file unless another custom server still uses it.
     */
    public static void deleteIfUnused(String name) {
        if (name == null) {
            return;
        }
        for (CustomDnsServer server : Daedalus.configurations.getCustomDNSServers()) {
            if (name.equals(server.getCertificate())) {
                return;
            }
        }
        File file = getFile(name);
        if (file.exists() && !file.delete()) {
            Logger.warning("Cannot delete certificate " + file);
        }
        invalidate(name);
    }

    /**
     * The CN of a distinguished name, or the whole name when it has no CN.
     */
    public static String getCommonName(X500Principal principal) {
        String name = principal.getName(X500Principal.RFC2253);
        for (String part : name.split(",")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("CN=")) {
                return trimmed.substring(3);
            }
        }
        return name;
    }

    private static String fingerprint(byte[] encoded) throws GeneralSecurityException {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(encoded);
        StringBuilder builder = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private static synchronized void invalidate(String name) {
        factories.remove(name);
    }

    /**
     * TLS v1.2 socket factory for connections to the given server: the system CAs plus,
     * when the server has one, its own certificate. Shared by the VPN's TLS provider and
     * the DNS test.
     */
    public static synchronized SSLSocketFactory getSocketFactory(AbstractDnsServer server)
            throws GeneralSecurityException, IOException {
        String name = server.getCertificate();
        if (name == null || !exists(name)) {
            if (name != null) {
                Logger.warning("Certificate " + name + " of " + server.getRealName() + " is missing, using system CAs only");
            }
            name = SYSTEM_ONLY;
        }
        SSLSocketFactory factory = factories.get(name);
        if (factory == null) {
            factory = build(name);
            factories.put(name, factory);
        }
        return factory;
    }

    private static SSLSocketFactory build(String name) throws GeneralSecurityException, IOException {
        X509TrustManager systemTrust = createTrustManager(null);
        X509TrustManager serverTrust = null;
        if (!SYSTEM_ONLY.equals(name)) {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            int index = 0;
            for (X509Certificate certificate : load(name)) {
                keyStore.setCertificateEntry("server" + index++, certificate);
            }
            serverTrust = createTrustManager(keyStore);
        }
        SSLContext context = SSLContext.getInstance("TLSv1.2");
        context.init(null, new TrustManager[]{new CompositeTrustManager(systemTrust, serverTrust)}, null);
        return context.getSocketFactory();
    }

    private static X509TrustManager createTrustManager(KeyStore keyStore) throws GeneralSecurityException {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(keyStore);
        for (TrustManager manager : factory.getTrustManagers()) {
            if (manager instanceof X509TrustManager) {
                return (X509TrustManager) manager;
            }
        }
        throw new GeneralSecurityException("No X509TrustManager available");
    }

    /**
     * Accepts a server certificate chain when either the system CAs or the server's own
     * certificate trust it.
     */
    private static class CompositeTrustManager implements X509TrustManager {
        private final X509TrustManager system;
        private final X509TrustManager server;

        CompositeTrustManager(X509TrustManager system, X509TrustManager server) {
            this.system = system;
            this.server = server;
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            try {
                system.checkServerTrusted(chain, authType);
            } catch (CertificateException e) {
                if (server == null) {
                    throw e;
                }
                server.checkServerTrusted(chain, authType);
            }
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            system.checkClientTrusted(chain, authType);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            X509Certificate[] issuers = system.getAcceptedIssuers();
            if (server == null) {
                return issuers;
            }
            X509Certificate[] serverIssuers = server.getAcceptedIssuers();
            X509Certificate[] all = Arrays.copyOf(issuers, issuers.length + serverIssuers.length);
            System.arraycopy(serverIssuers, 0, all, issuers.length, serverIssuers.length);
            return all;
        }
    }
}
