package org.ozzy.util;

import java.security.cert.Certificate;
import java.util.Base64;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.jboss.logging.Logger;
import org.ozzy.model.MCPServer;

public final class CertificatePinningUtil {

    private static final Logger LOG = Logger.getLogger(CertificatePinningUtil.class);

    private CertificatePinningUtil() {
    }

    public static boolean isPinningEnabled(MCPServer server) {
        return server != null && server.getCertificate() != null && !server.getCertificate().isBlank();
    }

    public static boolean validatePinnedCertificate(MCPServer server) {
        if (!isPinningEnabled(server)) {
            return true;
        }
        if (server.getHost() == null || server.getHost().isBlank() || server.getPort() <= 0) {
            LOG.warnf("Pinned certificate validation failed: missing host/port for server %s", server.getId());
            return false;
        }
        if (server.getProtocol() != null && !server.getProtocol().equalsIgnoreCase("HTTPS")) {
            LOG.warnf("Pinned certificate validation failed: protocol not HTTPS for server %s", server.getId());
            return false;
        }

        String storedPem = normalizePem(server.getCertificate());
        String retrievedPem = normalizePem(retrieveCertificatePem(server.getHost(), server.getPort()));
        if (retrievedPem.isBlank()) {
            LOG.warnf("Pinned certificate validation failed: unable to retrieve certificate for %s:%d",
                    server.getHost(), server.getPort());
            return false;
        }
        boolean match = storedPem.equals(retrievedPem);
        if (!match) {
            LOG.warnf("Pinned certificate validation failed: certificate mismatch for %s:%d",
                    server.getHost(), server.getPort());
        }
        return match;
    }

    public static String retrieveCertificatePem(String host, int port) {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, null, null);

            SSLSocketFactory factory = sslContext.getSocketFactory();
            try (SSLSocket socket = (SSLSocket) factory.createSocket(host, port)) {
                socket.startHandshake();
                Certificate[] certs = socket.getSession().getPeerCertificates();
                return toPem(certs);
            }
        } catch (Exception e) {
            LOG.warnf(e, "Failed to retrieve certificate for %s:%d", host, port);
            return null;
        }
    }

    public static String normalizePem(String pem) {
        if (pem == null) {
            return "";
        }
        return pem.replaceAll("\\s+", "");
    }

    private static String toPem(Certificate[] certs) {
        if (certs == null || certs.length == 0) {
            return "";
        }
        StringBuilder pemCert = new StringBuilder();
        for (Certificate cert : certs) {
            try {
                String encodedCert = Base64.getEncoder().encodeToString(cert.getEncoded());
                String formattedCert = encodedCert.replaceAll("(.{64})", "$1\n");

                pemCert.append("-----BEGIN CERTIFICATE-----\n");
                pemCert.append(formattedCert);
                pemCert.append("\n-----END CERTIFICATE-----\n");
            } catch (Exception e) {
                LOG.warnf("Error processing certificate: %s", e.getMessage());
            }
        }
        return pemCert.toString();
    }
}
