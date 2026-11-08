package dev.mcpmcp.forge;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.mcpmcp.core.Jsons;
import dev.mcpmcp.core.McpProtocol;
import dev.mcpmcp.core.Schema;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.security.MessageDigest;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public final class LocalServer implements AutoCloseable {
    private static final int MAX_REQUEST_BYTES = 1024 * 1024;
    private static final String JSON = "application/json";
    private final HttpServer server;
    private final ExecutorService pool;
    private final Supplier<String> token;
    private final BiFunction<String, JsonObject, JsonObject> call;
    private final McpProtocol mcp;
    private final String instance;
    private volatile boolean closed;

    public LocalServer(int port, Supplier<String> token, BiFunction<String, JsonObject, JsonObject> call) throws Exception {
        this(port, token, call, java.util.UUID.randomUUID().toString());
    }

    public LocalServer(int port, Supplier<String> token, BiFunction<String, JsonObject, JsonObject> call, String instance) throws Exception {
        if (!java.util.UUID.fromString(instance).toString().equals(instance)) throw new IllegalArgumentException("invalid_instance");
        this.instance = instance;
        this.token = java.util.Objects.requireNonNull(token, "token");
        this.call = java.util.Objects.requireNonNull(call, "call");
        mcp = new McpProtocol(call); // Load embedded resources before binding a socket.
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 16);
        pool = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), r -> { Thread t = new Thread(r, "mcpmcp-http"); t.setDaemon(true); return t; },
            new ThreadPoolExecutor.AbortPolicy());
        try {
            server.setExecutor(pool);
            server.createContext("/", e -> {
                try (e) {
                    try {
                        if (!secure(e)) return;
                        String path = e.getRequestURI().getPath();
                        if (path.equals("/identity")) { handleIdentity(e); return; }
                        if (!authorize(e)) return;
                        if (path.equals("/mcp")) handleMcp(e);
                        else if (path.equals("/call")) handleLegacy(e);
                        else sendError(e, 404, "unknown_endpoint");
                    } catch (RuntimeException ignored) {
                        sendError(e, 500, "internal_error");
                    }
                } catch (IOException ignored) {
                    // The client may disconnect while a bounded response is being sent.
                }
            });
            server.start();
        } catch (RuntimeException | Error startupFailure) {
            server.stop(0);
            pool.shutdownNow();
            throw startupFailure;
        }
    }

    public int port() { return server.getAddress().getPort(); }

    private boolean secure(HttpExchange e) throws IOException {
        if (e.getRequestHeaders().get("Host") == null || e.getRequestHeaders().get("Host").size() != 1
                || !("127.0.0.1:" + port()).equals(e.getRequestHeaders().getFirst("Host"))
                || e.getRequestHeaders().containsKey("Origin")) {
            sendError(e, 403, "forbidden_host_or_origin");
            return false;
        }
        return true;
    }

    private boolean authorize(HttpExchange e) throws IOException {
        String secret;
        try { secret = token.get(); } catch (RuntimeException invalid) { secret = null; }
        String auth = e.getRequestHeaders().getFirst("Authorization");
        if (e.getRequestHeaders().get("Authorization") == null || e.getRequestHeaders().get("Authorization").size() != 1
                || auth == null || secret == null || secret.isEmpty() || !MessageDigest.isEqual(auth.getBytes(StandardCharsets.UTF_8),
                ("Bearer " + secret).getBytes(StandardCharsets.UTF_8))) {
            sendError(e, 401, "unauthorized");
            return false;
        }
        return true;
    }

    /** Challenge proof allows the legacy bridge to authenticate this listener
     * before disclosing its bearer. It is not an alternative authorization path. */
    private void handleIdentity(HttpExchange e) throws IOException {
        if (!e.getRequestMethod().equals("GET")) {
            e.getResponseHeaders().set("Allow", "GET");
            sendError(e, 405, "method_not_allowed"); return;
        }
        String query = e.getRequestURI().getRawQuery();
        if (query == null || !query.matches("challenge=[a-f0-9]{64}")) {
            sendError(e, 400, "invalid_challenge"); return;
        }
        String challenge = query.substring("challenge=".length());
        try {
            String secret = token.get();
            if (secret == null || secret.isEmpty()) throw new IllegalStateException();
            String message = "mcpmcp-identity-v1\n" + instance + "\n" + ProcessHandle.current().pid() + "\n" + port() + "\n" + challenge;
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String proof = java.util.HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
            sendJson(e, 200, Jsons.obj("instance", instance, "pid", ProcessHandle.current().pid(), "port", port(), "challenge", challenge, "proof", proof));
        } catch (Exception unavailable) { sendError(e, 503, "identity_unavailable"); }
    }

    private void handleLegacy(HttpExchange e) throws IOException {
        if (!e.getRequestMethod().equals("POST")) { sendError(e, 404, "unknown_endpoint"); return; }
        if (!jsonContentType(e)) { sendError(e, 415, "json_required"); return; }
        byte[] body = readBody(e);
        if (body == null) { sendError(e, 413, "request_too_large"); return; }
        try {
            JsonObject request = McpProtocol.parse(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!request.keySet().equals(java.util.Set.of("method", "args"))) throw new IllegalArgumentException();
            Schema.bounded(request);
            JsonObject result = call.apply(Jsons.str(request, "method", ""), Jsons.object(request, "args"));
            sendJson(e, 200, result);
        } catch (Exception invalid) {
            sendJson(e, 400, Jsons.obj("error", "invalid_or_failed_request", "reason", invalid.getClass().getSimpleName()));
        }
    }

    private void handleMcp(HttpExchange e) throws IOException {
        if (e.getRequestHeaders().containsKey("MCP-Protocol-Version")
                && e.getRequestHeaders().get("MCP-Protocol-Version").size() != 1) {
            sendError(e, 400, "unsupported_protocol_version"); return;
        }
        String version = e.getRequestHeaders().getFirst("MCP-Protocol-Version");
        if (version == null) version = "2025-03-26";
        if (!McpProtocol.supportsVersion(version)) { sendError(e, 400, "unsupported_protocol_version"); return; }
        String method = e.getRequestMethod();
        if (method.equals("GET")) {
            e.getResponseHeaders().set("Allow", "POST");
            sendError(e, 405, "server_sent_events_not_supported");
            return;
        }
        if (!method.equals("POST")) {
            e.getResponseHeaders().set("Allow", "GET, POST");
            sendError(e, 405, "method_not_allowed");
            return;
        }
        if (!jsonContentType(e)) { sendError(e, 415, "json_required"); return; }
        String accept = String.join(",", e.getRequestHeaders().getOrDefault("Accept", java.util.List.of()));
        if (!accepts(accept, "application/json") || !accepts(accept, "text/event-stream")) {
            sendError(e, 406, "accept_json_and_event_stream_required"); return;
        }
        byte[] body = readBody(e);
        if (body == null) { sendError(e, 413, "request_too_large"); return; }
        String json;
        try {
            json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(body)).toString();
        } catch (CharacterCodingException invalidUtf8) {
            sendJson(e, 400, McpProtocol.error(null, -32700, "Invalid UTF-8"));
            return;
        }
        JsonObject response = mcp.handle(json);
        if (response == null) { e.getResponseHeaders().set("Cache-Control", "no-store"); e.sendResponseHeaders(202, -1); return; }
        int status = 200;
        if (response.has("error")) {
            int code = response.getAsJsonObject("error").get("code").getAsInt();
            if (code == -32700 || code == -32600) status = 400;
        }
        sendJson(e, status, response);
    }

    private static boolean jsonContentType(HttpExchange e) {
        if (e.getRequestHeaders().get("Content-Type") == null || e.getRequestHeaders().get("Content-Type").size() != 1) return false;
        String value = e.getRequestHeaders().getFirst("Content-Type");
        if (value == null) return false;
        String[] parts = value.split(";");
        if (!parts[0].trim().equalsIgnoreCase(JSON)) return false;
        for (int i = 1; i < parts.length; i++) {
            String[] pair = parts[i].trim().split("=", 2);
            if (pair[0].trim().equalsIgnoreCase("charset") && (pair.length != 2
                    || !pair[1].trim().replace("\"", "").equalsIgnoreCase("utf-8"))) return false;
        }
        return true;
    }

    private static boolean accepts(String header, String wanted) {
        if (header == null) return false;
        for (String entry : header.split(",")) {
            String[] parts = entry.trim().split(";", 2);
            if (!parts[0].trim().equalsIgnoreCase(wanted)) continue;
            if (parts.length == 1) return true;
            for (String parameter : parts[1].split(";")) {
                String[] pair = parameter.trim().split("=", 2);
                if (pair.length == 2 && pair[0].trim().equalsIgnoreCase("q")) {
                    try {
                        double q = Double.parseDouble(pair[1].trim());
                        if (!Double.isFinite(q) || q <= 0 || q > 1) return false;
                    }
                    catch (NumberFormatException invalid) { return false; }
                }
            }
            return true;
        }
        return false;
    }

    private static byte[] readBody(HttpExchange e) throws IOException {
        byte[] body = e.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
        return body.length > MAX_REQUEST_BYTES ? null : body;
    }

    private static void sendError(HttpExchange e, int status, String code) throws IOException {
        sendJson(e, status, Jsons.error(code));
    }

    private static void sendJson(HttpExchange e, int status, JsonObject value) throws IOException {
        byte[] bytes;
        try { bytes = McpProtocol.encode(value); }
        catch (IllegalArgumentException invalidOutput) {
            if (value != null && value.has("jsonrpc")) {
                status = 200;
                bytes = McpProtocol.encode(McpProtocol.error(value.get("id"), -32603, "Response exceeds output limits"));
            } else {
                status = 413;
                bytes = "{\"error\":\"response_too_large\"}".getBytes(StandardCharsets.UTF_8);
            }
        }
        e.getResponseHeaders().set("Content-Type", JSON + "; charset=utf-8");
        e.getResponseHeaders().set("Cache-Control", "no-store");
        e.sendResponseHeaders(status, bytes.length);
        e.getResponseBody().write(bytes);
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        server.stop(0);
        pool.shutdownNow();
        try {
            pool.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
