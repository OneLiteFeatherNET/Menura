package net.onelitefeather.menura.server.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-process stand-in for Entra ID on the loopback interface: discovery, JWKS, an authorization endpoint that
 * approves at once, and a token endpoint that checks the client secret and PKCE. No network beyond 127.0.0.1.
 * Each instance has its own key and its own grants, so tests share nothing.
 */
final class FakeEntraProvider implements AutoCloseable {

    static final String CLIENT_ID = "menura-web-test";
    static final String CLIENT_SECRET = "fake-client-secret";
    static final String TENANT_PATH = "/tenant/v2.0";

    private record Grant(String nonce, String challenge, String subject, List<String> groups) {
    }

    private final RSAKey key = TestKeys.newRsaKey("fake-entra");
    private final HttpServer server;
    private final String issuer;
    private final Map<String, Grant> grants = new ConcurrentHashMap<>();
    private volatile String subject = "entra-user-1";
    private volatile List<String> groups = List.of();
    private volatile String nonceOverride;

    FakeEntraProvider() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        issuer = "http://127.0.0.1:" + server.getAddress().getPort() + TENANT_PATH;
        server.createContext(TENANT_PATH + "/.well-known/openid-configuration", exchange -> reply(exchange, 200,
                discoveryJson()));
        server.createContext("/tenant/discovery/v2.0/keys", exchange -> reply(exchange, 200,
                new JWKSet(key.toPublicJWK()).toString()));
        server.createContext("/tenant/oauth2/v2.0/authorize", this::authorize);
        server.createContext("/tenant/oauth2/v2.0/token", this::token);
        server.start();
    }

    /** The issuer as Menura is configured with it: the tenant path on the loopback interface. */
    String issuer() {
        return issuer;
    }

    /** The user the next authorization approves, with the group ids the id token will carry. */
    void approveNextSignInAs(String newSubject, List<String> newGroups) {
        this.subject = newSubject;
        this.groups = List.copyOf(newGroups);
    }

    /** Replaces the nonce in the next id token, to show that a mismatching nonce is refused. */
    void echoWrongNonce(String wrongNonce) {
        this.nonceOverride = wrongNonce;
    }

    private void authorize(HttpExchange exchange) throws IOException {
        Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
        String code = Secrets.generate();
        grants.put(code, new Grant(query.get("nonce"), query.get("code_challenge"), subject, groups));
        String location = query.get("redirect_uri") + "?code=" + code + "&state=" + query.get("state");
        exchange.getResponseHeaders().add("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private void token(HttpExchange exchange) throws IOException {
        Map<String, String> form = query(readBody(exchange.getRequestBody()));
        Grant grant = form.containsKey("code") ? grants.remove(form.get("code")) : null;
        boolean secretOk = CLIENT_SECRET.equals(form.get("client_secret"));
        boolean pkceOk = grant != null && Pkce.s256Challenge(form.getOrDefault("code_verifier", ""))
                .equals(grant.challenge());
        if (grant == null || !secretOk || !pkceOk) {
            reply(exchange, 400, "{\"error\":\"invalid_grant\"}");
            return;
        }
        String nonce = nonceOverride != null ? nonceOverride : grant.nonce();
        reply(exchange, 200, "{\"token_type\":\"Bearer\",\"id_token\":\"" + idToken(grant, nonce) + "\"}");
    }

    private String idToken(Grant grant, String nonce) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .audience(CLIENT_ID)
                    .subject(grant.subject())
                    .issueTime(new Date())
                    .expirationTime(new Date(4_102_444_800_000L)) // 2100-01-01: the upstream expiry is not under test
                    .claim("nonce", nonce)
                    .claim("groups", grant.groups())
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                    claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private String discoveryJson() {
        return "{\"issuer\":\"" + issuer + "\","
                + "\"authorization_endpoint\":\"" + issuer.replace(TENANT_PATH, "") + "/tenant/oauth2/v2.0/authorize\","
                + "\"token_endpoint\":\"" + issuer.replace(TENANT_PATH, "") + "/tenant/oauth2/v2.0/token\","
                + "\"jwks_uri\":\"" + issuer.replace(TENANT_PATH, "") + "/tenant/discovery/v2.0/keys\"}";
    }

    private static Map<String, String> query(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, String> values = new HashMap<>();
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String name = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            values.put(name, value);
        }
        return values;
    }

    private static String readBody(InputStream body) throws IOException {
        try (InputStream in = body) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().collect(Collectors.joining());
        }
    }

    private static void reply(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
