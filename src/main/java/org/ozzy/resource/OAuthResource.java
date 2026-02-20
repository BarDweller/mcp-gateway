package org.ozzy.resource;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.jboss.logging.Logger;
import org.ozzy.model.MCPServer;
import org.ozzy.service.MCPServerService;

@ApplicationScoped
@Path("/oauth")
public class OAuthResource {

    private static final Logger LOG = Logger.getLogger(OAuthResource.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, PendingOAuth> pending = new ConcurrentHashMap<>();

    @Inject
    MCPServerService serverService;

    @Inject
    ObjectMapper objectMapper;

    @GET
    @Path("/connect/{id}")
    public Response connect(@PathParam("id") String id, @Context UriInfo uriInfo) {
        MCPServer server = serverService.getServer(id);
        if (server == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("Server not found").build();
        }

        String resourceUrl = buildResourceUrl(server);
        if (resourceUrl == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Server URL not configured").build();
        }

        DiscoveryResult discovery = discoverAuthorizationServer(server, resourceUrl);
        if (discovery == null || discovery.authorizationServer == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Authorization server discovery failed")
                    .build();
        }

        OAuthMetadata metadata = fetchAuthorizationServerMetadata(discovery.authorizationServer);
        if (metadata == null || metadata.authorizationEndpoint == null || metadata.tokenEndpoint == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Authorization server metadata missing required endpoints")
                    .build();
        }

        String redirectUri = uriInfo.getBaseUri().toString() + "oauth/callback";

        String clientId = server.getOauthClientId();
        if (clientId == null || clientId.isBlank()) {
            if (metadata.registrationEndpoint == null || metadata.registrationEndpoint.isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("OAuth registration endpoint missing for dynamic registration")
                        .build();
            }
            clientId = registerClient(metadata.registrationEndpoint, redirectUri);
            if (clientId == null || clientId.isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("OAuth dynamic registration failed")
                        .build();
            }
            server.setOauthClientId(clientId);
            serverService.updateServer(server.getId(), server);
        }

        String state = randomUrlSafe(18);
        String codeVerifier = randomUrlSafe(48);
        String codeChallenge = sha256Base64Url(codeVerifier);

        String scope = discovery.scope;
        String authorizationUrl = buildAuthorizationUrl(metadata.authorizationEndpoint, clientId, redirectUri,
            codeChallenge, state, scope, resourceUrl);

        pending.put(state, new PendingOAuth(server.getId(), metadata.tokenEndpoint, clientId, codeVerifier, redirectUri, resourceUrl));
        return Response.seeOther(URI.create(authorizationUrl)).build();
    }

    @GET
    @Path("/callback")
    @Produces(MediaType.TEXT_HTML)
    public Response callback(@QueryParam("code") String code, @QueryParam("state") String state) {
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(buildCallbackHtml(false, null, "Missing authorization response"))
                    .build();
        }

        PendingOAuth pendingState = pending.remove(state);
        if (pendingState == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(buildCallbackHtml(false, null, "OAuth state not found"))
                    .build();
        }

        TokenResponse tokens = exchangeToken(pendingState, code);
        if (tokens == null || tokens.accessToken == null || tokens.accessToken.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(buildCallbackHtml(false, pendingState.serverId, "OAuth token exchange failed"))
                    .build();
        }

        MCPServer server = serverService.getServer(pendingState.serverId);
        if (server != null) {
            server.setOauthAccessToken(tokens.accessToken);
            server.setOauthRefreshToken(tokens.refreshToken);
            serverService.updateServer(server.getId(), server);
        }

        return Response.ok(buildCallbackHtml(true, pendingState.serverId, null)).build();
    }

    private OAuthMetadata fetchAuthorizationServerMetadata(String issuer) {
        List<String> urls = buildMetadataUrls(issuer);
        for (String url : urls) {
            OAuthMetadata metadata = fetchMetadataUrl(url);
            if (metadata != null && metadata.authorizationEndpoint != null && metadata.tokenEndpoint != null) {
                return metadata;
            }
        }
        return null;
    }

    private OAuthMetadata fetchMetadataUrl(String metadataUrl) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(metadataUrl))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            JsonNode root = objectMapper.readTree(response.body());
            OAuthMetadata metadata = new OAuthMetadata();
            metadata.authorizationEndpoint = textOrNull(root, "authorization_endpoint");
            metadata.tokenEndpoint = textOrNull(root, "token_endpoint");
            metadata.registrationEndpoint = textOrNull(root, "registration_endpoint");
            return metadata;
        } catch (Exception e) {
            return null;
        }
    }

    private String registerClient(String registrationEndpoint, String redirectUri) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("client_name", "MCP Gateway");
            ArrayNode redirectUris = payload.putArray("redirect_uris");
            redirectUris.add(redirectUri);
            ArrayNode grantTypes = payload.putArray("grant_types");
            grantTypes.add("authorization_code");
            ArrayNode responseTypes = payload.putArray("response_types");
            responseTypes.add("code");
            payload.put("token_endpoint_auth_method", "none");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(registrationEndpoint))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOG.warnf("OAuth registration failed: %s", response.statusCode());
                return null;
            }
            JsonNode root = objectMapper.readTree(response.body());
            return textOrNull(root, "client_id");
        } catch (Exception e) {
            LOG.warnf(e, "OAuth registration failed");
            return null;
        }
    }

    private TokenResponse exchangeToken(PendingOAuth pendingState, String code) {
        try {
            String body = buildFormBody(Map.of(
                    "grant_type", "authorization_code",
                    "code", code,
                    "redirect_uri", pendingState.redirectUri,
                    "client_id", pendingState.clientId,
                    "code_verifier", pendingState.codeVerifier,
                    "resource", pendingState.resourceUrl
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(pendingState.tokenEndpoint))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOG.warnf("OAuth token exchange failed: %s", response.statusCode());
                return null;
            }
            JsonNode root = objectMapper.readTree(response.body());
            TokenResponse tokens = new TokenResponse();
            tokens.accessToken = textOrNull(root, "access_token");
            tokens.refreshToken = textOrNull(root, "refresh_token");
            return tokens;
        } catch (Exception e) {
            LOG.warnf(e, "OAuth token exchange failed");
            return null;
        }
    }

    private String buildAuthorizationUrl(String authorizationEndpoint, String clientId, String redirectUri,
                                         String codeChallenge, String state, String scope, String resourceUrl) {
        StringBuilder url = new StringBuilder(authorizationEndpoint);
        if (!authorizationEndpoint.contains("?")) {
            url.append("?");
        } else if (!authorizationEndpoint.endsWith("&") && !authorizationEndpoint.endsWith("?")) {
            url.append("&");
        }
        url.append("response_type=code");
        url.append("&client_id=").append(encode(clientId));
        url.append("&redirect_uri=").append(encode(redirectUri));
        url.append("&code_challenge=").append(encode(codeChallenge));
        url.append("&code_challenge_method=S256");
        url.append("&state=").append(encode(state));
        url.append("&resource=").append(encode(resourceUrl));
        if (scope != null && !scope.isBlank()) {
            url.append("&scope=").append(encode(scope));
        }
        return url.toString();
    }

    private String buildFormBody(Map<String, String> params) {
        StringBuilder body = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                body.append("&");
            }
            first = false;
            body.append(encode(entry.getKey())).append("=").append(encode(entry.getValue()));
        }
        return body.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String randomUrlSafe(int byteLength) {
        byte[] bytes = new byte[byteLength];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Base64Url(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute code challenge", e);
        }
    }

    private DiscoveryResult discoverAuthorizationServer(MCPServer server, String resourceUrl) {
        HttpClient client = HttpClient.newHttpClient();
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("jsonrpc", "2.0");
            payload.put("id", 1);
            payload.put("method", "tools/list");
            payload.set("params", objectMapper.createObjectNode());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(resourceUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) {
                DiscoveryResult fromHeader = parseWwwAuthenticate(response.headers().allValues("WWW-Authenticate"));
                if (fromHeader != null && fromHeader.resourceMetadataUrl != null) {
                    return fetchProtectedResourceMetadata(fromHeader.resourceMetadataUrl, fromHeader.scope);
                }
                return fetchProtectedResourceMetadataFromWellKnown(resourceUrl, null);
            }
            LOG.warnf("OAuth discovery skipped: expected 401 but received %s", response.statusCode());
        } catch (Exception e) {
            LOG.warnf(e, "OAuth discovery request failed");
        }
        return null;
    }

    private DiscoveryResult parseWwwAuthenticate(List<String> headers) {
        if (headers == null) {
            return null;
        }
        Pattern metadataPattern = Pattern.compile("resource_metadata=\"([^\"]+)\"");
        Pattern scopePattern = Pattern.compile("scope=\"([^\"]+)\"");
        for (String header : headers) {
            if (header == null) {
                continue;
            }
            Matcher metadata = metadataPattern.matcher(header);
            Matcher scope = scopePattern.matcher(header);
            DiscoveryResult result = new DiscoveryResult();
            if (metadata.find()) {
                result.resourceMetadataUrl = metadata.group(1);
            }
            if (scope.find()) {
                result.scope = scope.group(1);
            }
            if (result.resourceMetadataUrl != null) {
                return result;
            }
        }
        return null;
    }

    private DiscoveryResult fetchProtectedResourceMetadata(String metadataUrl, String scopeOverride) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(metadataUrl))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOG.warnf("Protected resource metadata request failed: %s", response.statusCode());
                return null;
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode authServers = root.get("authorization_servers");
            String authorizationServer = null;
            if (authServers != null && authServers.isArray() && authServers.size() > 0) {
                authorizationServer = authServers.get(0).asText(null);
            }
            if (authorizationServer == null || authorizationServer.isBlank()) {
                return null;
            }
            DiscoveryResult result = new DiscoveryResult();
            result.authorizationServer = authorizationServer;
            result.scope = scopeOverride;
            return result;
        } catch (Exception e) {
            LOG.warnf(e, "Failed to fetch protected resource metadata");
            return null;
        }
    }

    private DiscoveryResult fetchProtectedResourceMetadataFromWellKnown(String resourceUrl, String scopeOverride) {
        for (String candidate : buildWellKnownResourceMetadataUrls(resourceUrl)) {
            DiscoveryResult result = fetchProtectedResourceMetadata(candidate, scopeOverride);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private List<String> buildWellKnownResourceMetadataUrls(String resourceUrl) {
        try {
            URI uri = URI.create(resourceUrl);
            String base = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
            String path = uri.getPath();
            if (path == null) {
                path = "";
            }
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            if (path.isBlank()) {
                path = "mcp";
            }
            String withPath = base + "/.well-known/oauth-protected-resource/" + path;
            String root = base + "/.well-known/oauth-protected-resource";
            return List.of(withPath, root);
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> buildMetadataUrls(String issuer) {
        try {
            URI uri = URI.create(issuer);
            String base = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
            String path = uri.getPath();
            if (path == null || path.isBlank() || "/".equals(path)) {
                return List.of(
                        base + "/.well-known/oauth-authorization-server",
                        base + "/.well-known/openid-configuration"
                );
            }
            String trimmedPath = path.startsWith("/") ? path.substring(1) : path;
            return List.of(
                    base + "/.well-known/oauth-authorization-server/" + trimmedPath,
                    base + "/.well-known/openid-configuration/" + trimmedPath,
                    base + path + (path.endsWith("/") ? "" : "/") + ".well-known/openid-configuration"
            );
        } catch (Exception e) {
            return List.of();
        }
    }

    private String buildResourceUrl(MCPServer server) {
        if (server == null || server.getHost() == null || server.getHost().isBlank() || server.getPort() <= 0) {
            return null;
        }
        String protocol = server.getProtocol() != null && server.getProtocol().equalsIgnoreCase("HTTPS") ? "https" : "http";
        String path = server.getRemotePath();
        if (path == null || path.isBlank()) {
            path = "/mcp";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return protocol + "://" + server.getHost() + ":" + server.getPort() + path;
    }

    private String textOrNull(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value != null && !value.isNull() ? value.asText() : null;
    }

    private String buildCallbackHtml(boolean success, String serverId, String error) {
        String safeServerId = serverId == null ? "" : serverId.replace("\"", "");
        String safeError = error == null ? "" : error.replace("\"", "");
        String payload = "{type:'oauth-complete',success:" + success
                + ",serverId:'" + safeServerId + "',error:'" + safeError + "'}";
        return "<!doctype html><html><head><meta charset='utf-8'></head><body>"
                + "<script>(function(){try{if(window.opener){window.opener.postMessage(" + payload
                + ", window.location.origin);} }catch(e){} window.close();})();</script>"
                + (success ? "<p>OAuth connection complete. You can close this window.</p>"
                        : "<p>OAuth connection failed. You can close this window.</p>")
                + "</body></html>";
    }

    private static final class OAuthMetadata {
        private String authorizationEndpoint;
        private String tokenEndpoint;
        private String registrationEndpoint;
    }

    private static final class PendingOAuth {
        private final String serverId;
        private final String tokenEndpoint;
        private final String clientId;
        private final String codeVerifier;
        private final String redirectUri;
        private final String resourceUrl;

        private PendingOAuth(String serverId, String tokenEndpoint, String clientId, String codeVerifier, String redirectUri, String resourceUrl) {
            this.serverId = serverId;
            this.tokenEndpoint = tokenEndpoint;
            this.clientId = clientId;
            this.codeVerifier = codeVerifier;
            this.redirectUri = redirectUri;
            this.resourceUrl = resourceUrl;
        }
    }

    private static final class DiscoveryResult {
        private String resourceMetadataUrl;
        private String authorizationServer;
        private String scope;
    }

    private static final class TokenResponse {
        private String accessToken;
        private String refreshToken;
    }
}