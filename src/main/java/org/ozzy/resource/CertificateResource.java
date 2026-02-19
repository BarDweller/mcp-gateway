package org.ozzy.resource;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.security.cert.Certificate;
import java.util.Base64;
import java.util.Map;

import org.jboss.logging.Logger;

@Path("/certificate")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CertificateResource {

    private static final Logger LOG = Logger.getLogger(CertificateResource.class);

    @GET
    @Path("/retrieve-certificate/{host}/{port}")
    public Response retrieveCertificate(@PathParam("host") String host, @PathParam("port") int port) {
        LOG.infof("Retrieving certificate for %s:%d", host, port);
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, null, null);

            SSLSocketFactory factory = sslContext.getSocketFactory();
            try (SSLSocket socket = (SSLSocket) factory.createSocket(host, port)) {
                socket.startHandshake();
                Certificate[] certs = socket.getSession().getPeerCertificates();

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

                return Response.ok(pemCert.toString()).build();
            }
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Failed to retrieve certificate: " + e.getMessage())
                    .build();
        }
    }

    @POST
    @Path("/test-certificate")
    public Response testCertificate(Map<String, String> request) {
        String host = request.get("host");
        int port = Integer.parseInt(request.get("port"));
        String storedPem = request.get("certificate");

        StringBuilder reformattedPem = new StringBuilder();
        String[] certBlocks = storedPem.split("(-----END CERTIFICATE-----)");

        for (String block : certBlocks) {
            if (block.contains("-----BEGIN CERTIFICATE-----")) {
                String[] parts = block.split("-----BEGIN CERTIFICATE-----");
                reformattedPem.append("-----BEGIN CERTIFICATE-----\n");
                if (parts.length > 1) {
                    String base64Data = parts[1].replaceAll("[^a-zA-Z0-9+/=]", "");
                    base64Data = base64Data.replaceAll("(.{64})", "$1\n");
                    reformattedPem.append(base64Data).append("\n");
                }
                reformattedPem.append("-----END CERTIFICATE-----\n");
            }
        }
        storedPem = reformattedPem.toString();

        LOG.infof("Testing certificate for host: %s, port: %d", host, port);

        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, null, null);

            SSLSocketFactory factory = sslContext.getSocketFactory();
            try (SSLSocket socket = (SSLSocket) factory.createSocket(host, port)) {
                socket.startHandshake();
                Certificate[] certs = socket.getSession().getPeerCertificates();

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

                String retrievedPem = pemCert.toString().trim();
                storedPem = storedPem.trim();

                if (retrievedPem.equals(storedPem)) {
                    return Response.ok("Certificate matches the stored PEM.").build();
                }

                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("Certificate does not match the stored PEM.")
                        .build();
            }
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Failed to test certificate: " + e.getMessage())
                    .build();
        }
    }
}
