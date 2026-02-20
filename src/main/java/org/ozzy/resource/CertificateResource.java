package org.ozzy.resource;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

import org.jboss.logging.Logger;
import org.ozzy.util.CertificatePinningUtil;

@Path("/certificate")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CertificateResource {

    private static final Logger LOG = Logger.getLogger(CertificateResource.class);

    @GET
    @Path("/retrieve-certificate/{host}/{port}")
    public Response retrieveCertificate(@PathParam("host") String host, @PathParam("port") int port) {
        LOG.infof("Retrieving certificate for %s:%d", host, port);
        String pem = CertificatePinningUtil.retrieveCertificatePem(host, port);
        if (pem == null || pem.isBlank()) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Failed to retrieve certificate.")
                    .build();
        }
        return Response.ok(pem).build();
    }

    @POST
    @Path("/test-certificate")
    public Response testCertificate(Map<String, String> request) {
        String host = request.get("host");
        int port = Integer.parseInt(request.get("port"));
        String storedPem = request.get("certificate");

        LOG.infof("Testing certificate for host: %s, port: %d", host, port);
        String retrievedPem = CertificatePinningUtil.retrieveCertificatePem(host, port);
        if (retrievedPem == null || retrievedPem.isBlank()) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Failed to test certificate: unable to retrieve certificate.")
                    .build();
        }

        String normalizedStored = CertificatePinningUtil.normalizePem(storedPem);
        String normalizedRetrieved = CertificatePinningUtil.normalizePem(retrievedPem);

        if (normalizedRetrieved.equals(normalizedStored)) {
            return Response.ok("Certificate matches the stored PEM.").build();
        }

        return Response.status(Response.Status.BAD_REQUEST)
                .entity("Certificate does not match the stored PEM.")
                .build();
    }
}
