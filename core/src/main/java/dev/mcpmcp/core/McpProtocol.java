package dev.mcpmcp.core;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;

/** Stateless MCP JSON-RPC handler. All game access remains in the supplied dispatcher. */
public final class McpProtocol {
    public static final String LATEST_VERSION = "2025-11-25";
    public static final int MAX_RESPONSE_BYTES = 12 * 1024 * 1024;
    private static final Gson WIRE_GSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
    private static final Set<String> VERSIONS = Set.of(LATEST_VERSION, "2025-06-18", "2025-03-26");
    private static final JsonObject CATALOG = loadCatalog();
    private static final Map<String, JsonObject> TOOLS = indexTools();
    private final BiFunction<String, JsonObject, JsonObject> call;

    private static JsonObject loadCatalog() {
        try (InputStream in = McpProtocol.class.getResourceAsStream("/dev/mcpmcp/mcp-tools.json")) {
            if (in == null) throw new IllegalStateException("Missing embedded MCP tool catalog");
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException invalid) {
            throw new IllegalStateException("Cannot load embedded MCP tool catalog", invalid);
        }
    }

    private static Map<String, JsonObject> indexTools() {
        Map<String, JsonObject> tools = new LinkedHashMap<>();
        for (JsonElement value : CATALOG.getAsJsonArray("tools")) {
            JsonObject tool = value.getAsJsonObject();
            if (tools.put(tool.get("name").getAsString(), tool) != null)
                throw new IllegalStateException("Duplicate embedded MCP tool name");
        }
        return java.util.Collections.unmodifiableMap(tools);
    }

    public McpProtocol(BiFunction<String, JsonObject, JsonObject> call) {
        this.call = Objects.requireNonNull(call, "call");
    }

    public static boolean supportsVersion(String version) {
        return VERSIONS.contains(version);
    }

    /** Returns null for accepted notifications and client responses. */
    public JsonObject handle(String json) {
        JsonElement value;
        try { value = parse(json); }
        catch (LimitException invalid) { return error(null, -32600, "Message exceeds JSON limits"); }
        catch (IOException | IllegalArgumentException invalid) { return error(null, -32700, "Parse error"); }
        if (!value.isJsonObject()) return error(null, -32600, "Invalid request (batch is not supported)");
        JsonObject request = value.getAsJsonObject();
        if (!isString(request.get("jsonrpc")) || !request.get("jsonrpc").getAsString().equals("2.0")
                || (request.has("id") && !validId(request.get("id"))))
            return error(null, -32600, "Invalid request");
        JsonElement id = request.get("id");
        if (!request.has("method")) {
            if (!request.has("id") || request.has("result") == request.has("error") || request.has("params")
                    || (request.has("result") && !request.get("result").isJsonObject()))
                return error(null, -32600, "Invalid client response");
            if (request.has("error")) {
                JsonElement e = request.get("error");
                if (!e.isJsonObject() || !isInteger(e.getAsJsonObject().get("code"))
                        || !isString(e.getAsJsonObject().get("message")))
                    return error(null, -32600, "Invalid client response");
            }
            return null; // No server-initiated requests or state to correlate in this profile.
        }
        if (!isString(request.get("method")) || request.has("result") || request.has("error"))
            return error(null, -32600, "Invalid request");
        if (request.has("params") && !request.get("params").isJsonObject())
            return request.has("id") ? error(id, -32602, "params must be an object") : error(null, -32600, "Invalid notification");
        if (!request.has("id")) return null; // A notification can never trigger a game call.
        String method = request.get("method").getAsString();
        JsonObject params = Jsons.object(request, "params");
        JsonObject result;
        try {
            if (method.equals("initialize")) {
                String version = Jsons.str(params, "protocolVersion", "");
                if (version.isEmpty() || !params.has("capabilities") || !params.get("capabilities").isJsonObject())
                    throw new IllegalArgumentException();
                JsonObject info = Jsons.object(params, "clientInfo");
                if (Jsons.str(info, "name", "").isBlank() || Jsons.str(info, "version", "").isBlank())
                    throw new IllegalArgumentException();
                if (version.equals("2024-11-05"))
                    return error(id, -32602, "2024-11-05 HTTP+SSE is not supported; use Streamable HTTP 2025-03-26 or later");
                if (!supportsVersion(version)) version = LATEST_VERSION;
                result = Jsons.obj("protocolVersion", version, "serverInfo",
                    Jsons.obj("name", "mcpmcp", "version", "0.1.2"),
                    "capabilities", Jsons.obj("tools", new JsonObject()),
                    "instructions", CATALOG.get("instructions"));
            } else if (method.equals("ping")) {
                result = new JsonObject();
            } else if (method.equals("tools/list")) {
                if (params.has("cursor")) throw new IllegalArgumentException(); // Entire fixed catalog fits one page.
                result = Jsons.obj("tools", CATALOG.getAsJsonArray("tools").deepCopy());
            } else if (method.equals("tools/call")) {
                result = callTool(params, id);
            } else {
                return error(id, -32601, "Method not found");
            }
        } catch (IllegalArgumentException invalid) {
            return error(id, -32602, "Invalid params");
        }
        return Jsons.obj("jsonrpc", "2.0", "id", id, "result", result);
    }

    private JsonObject callTool(JsonObject params, JsonElement id) {
        if (!Set.of("name", "arguments", "_meta").containsAll(params.keySet())) throw new IllegalArgumentException();
        if (params.has("_meta") && !params.get("_meta").isJsonObject()) throw new IllegalArgumentException();
        String name = Jsons.str(params, "name", "");
        JsonObject tool = TOOLS.get(name);
        if (tool == null) throw new IllegalArgumentException();
        JsonObject args = Jsons.object(params, "arguments").deepCopy();
        validate(args, tool.getAsJsonObject("inputSchema"), 0, new int[]{8192});
        if (name.equals("execute_action") && !args.has("action_id"))
            args.addProperty("action_id", UUID.randomUUID().toString().replace("-", ""));
        JsonObject data;
        try {
            JsonObject aliases = CATALOG.getAsJsonObject("aliases");
            if (aliases.has(name)) {
                data = call.apply("observe", Jsons.obj("kind", aliases.get(name), "arguments", args));
            } else {
                data = call.apply(Jsons.str(CATALOG.getAsJsonObject("methods"), name, name), args);
            }
            if (data == null) throw new IllegalStateException();
        } catch (RuntimeException failed) {
            data = Jsons.error("dispatch_failed");
            if (name.equals("execute_action")) {
                data.add("action_id", args.get("action_id"));
                data.addProperty("delivery_unknown", true);
                data.addProperty("retry_note", "Query this action_id before retrying; reuse it.");
            }
        }
        String encoded;
        try {
            encoded = new String(encode(data), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException invalidOutput) {
            data = Jsons.error("response_too_large_or_invalid");
            encoded = data.toString();
        }
        if (name.equals("execute_action")) {
            data = data.deepCopy();
            if (!data.has("action_id")) data.add("action_id", args.get("action_id"));
            String failure = isString(data.get("error")) ? data.get("error").getAsString() : "";
            if (Set.of("client_thread_timeout", "request_interrupted", "dispatch_failed", "response_too_large_or_invalid").contains(failure)) {
                data.addProperty("delivery_unknown", true);
                data.addProperty("retry_note", "Query this action_id before retrying; reuse it.");
            }
            try { encoded = new String(encode(data), StandardCharsets.UTF_8); }
            catch (IllegalArgumentException invalidOutput) { return toolOutputFailure(name, args); }
        }
        boolean failed = businessError(data, 0);
        if (!failed && name.equals("observe") && Jsons.str(args, "kind", "").equals("screenshot")) {
            JsonObject pic = data.has("data") && data.get("data").isJsonObject() ? data.getAsJsonObject("data") : data;
            if (isString(pic.get("base64")) && !pic.get("base64").getAsString().isEmpty()) {
                if (pic.has("mime") && !isString(pic.get("mime"))) return toolOutputFailure(name, args);
                return checkedToolResult(Jsons.obj("content", new JsonObject[]{Jsons.obj("type", "image", "data", pic.get("base64"),
                    "mimeType", Jsons.str(pic, "mime", "image/png"))}, "isError", false), name, args, id);
            }
        }
        return checkedToolResult(Jsons.obj("content", new JsonObject[]{Jsons.obj("type", "text", "text", encoded)}, "isError", failed), name, args, id);
    }

    private static JsonObject checkedToolResult(JsonObject result, String name, JsonObject args, JsonElement id) {
        try { encode(Jsons.obj("jsonrpc", "2.0", "id", id, "result", result)); }
        catch (IllegalArgumentException invalidOutput) { return toolOutputFailure(name, args); }
        return result;
    }

    private static JsonObject toolOutputFailure(String name, JsonObject args) {
        JsonObject data = Jsons.error("response_too_large_or_invalid");
        if (name.equals("execute_action")) {
            data.add("action_id", args.get("action_id"));
            data.addProperty("delivery_unknown", true);
            data.addProperty("retry_note", "Query this action_id before retrying; reuse it.");
        }
        return Jsons.obj("content", new JsonObject[]{Jsons.obj("type", "text", "text", data.toString())}, "isError", true);
    }

    /** Serializes through a capped UTF-8 sink; never allocates an unbounded response buffer. */
    public static byte[] encode(JsonElement value) {
        checkOutput(value, 0, new long[]{131072, MAX_RESPONSE_BYTES});
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        OutputStream capped = new OutputStream() {
            private int count;
            @Override public void write(int b) {
                if (++count > MAX_RESPONSE_BYTES) throw new IllegalArgumentException("response_too_large");
                bytes.write(b);
            }
            @Override public void write(byte[] b, int off, int len) {
                if ((long) count + len > MAX_RESPONSE_BYTES) throw new IllegalArgumentException("response_too_large");
                count += len;
                bytes.write(b, off, len);
            }
        };
        try (Writer writer = new OutputStreamWriter(capped, StandardCharsets.UTF_8)) {
            WIRE_GSON.toJson(value, writer);
        } catch (IOException impossible) {
            throw new IllegalArgumentException("response_serialization_failed");
        }
        return bytes.toByteArray();
    }

    private static void checkOutput(JsonElement value, int depth, long[] budget) {
        if (depth > 64 || --budget[0] < 0) throw new IllegalArgumentException("response_limits");
        if (value == null || value.isJsonNull()) return;
        if (value.isJsonObject()) {
            for (var entry : value.getAsJsonObject().entrySet()) {
                budget[1] -= entry.getKey().length();
                if (budget[1] < 0) throw new IllegalArgumentException("response_limits");
                checkOutput(entry.getValue(), depth + 1, budget);
            }
        } else if (value.isJsonArray()) {
            for (JsonElement item : value.getAsJsonArray()) checkOutput(item, depth + 1, budget);
        } else {
            String text = value.getAsString();
            budget[1] -= text.length();
            if (budget[1] < 0 || (value.getAsJsonPrimitive().isNumber() && !Double.isFinite(value.getAsDouble())))
                throw new IllegalArgumentException("response_limits");
        }
    }

    private static boolean businessError(JsonObject value, int depth) {
        if (value.has("error")) return true;
        for (String key : new String[]{"data", "result"})
            if (depth < 16 && value.has(key) && value.get(key).isJsonObject() && businessError(value.getAsJsonObject(key), depth + 1)) return true;
        return false;
    }

    // Implements the schema vocabulary used by the embedded bridge declarations.
    // Keep its 262144-character plugin text allowance separate from Schema's general 32768 limit.
    private static void validate(JsonElement value, JsonObject schema, int depth, int[] budget) {
        if (depth > 16 || --budget[0] < 0) throw new IllegalArgumentException();
        if (schema.has("enum")) {
            boolean found = false;
            for (JsonElement item : schema.getAsJsonArray("enum")) found |= item.equals(value);
            if (!found) throw new IllegalArgumentException();
        }
        switch (Jsons.str(schema, "type", "any")) {
            case "object": {
                if (!value.isJsonObject() || value.getAsJsonObject().size() > 128) throw new IllegalArgumentException();
                JsonObject obj = value.getAsJsonObject(), props = Jsons.object(schema, "properties");
                for (JsonElement required : Jsons.array(schema, "required"))
                    if (!obj.has(required.getAsString())) throw new IllegalArgumentException();
                for (var entry : obj.entrySet()) {
                    if (entry.getKey().length() > 256) throw new IllegalArgumentException();
                    if (props.has(entry.getKey())) validate(entry.getValue(), props.getAsJsonObject(entry.getKey()), depth + 1, budget);
                    else if (Jsons.bool(schema, "additionalProperties", false)) bounded(entry.getValue(), depth + 1, budget);
                    else throw new IllegalArgumentException();
                }
                break;
            }
            case "string": {
                if (!isString(value)) throw new IllegalArgumentException();
                String text = value.getAsString();
                int length = text.codePointCount(0, text.length());
                int min = Jsons.integer(schema, "minLength", 0);
                if (length < min || length > Jsons.integer(schema, "maxLength", 8192) || (min > 0 && text.isBlank())
                        || (schema.has("pattern") && !text.matches(schema.get("pattern").getAsString()))) throw new IllegalArgumentException();
                break;
            }
            case "integer": case "number": {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException();
                BigDecimal n = new BigDecimal(value.getAsString());
                BigDecimal min = schema.has("minimum") ? schema.get("minimum").getAsBigDecimal() : new BigDecimal("-1e30");
                BigDecimal max = schema.has("maximum") ? schema.get("maximum").getAsBigDecimal() : new BigDecimal("1e30");
                if (n.compareTo(min) < 0 || n.compareTo(max) > 0
                        || (Jsons.str(schema, "type", "").equals("integer") && !isInteger(value))) throw new IllegalArgumentException();
                break;
            }
            case "boolean":
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException();
                break;
            default: bounded(value, depth, budget);
        }
    }

    private static void bounded(JsonElement value, int depth, int[] budget) {
        if (depth > 16 || --budget[0] < 0) throw new IllegalArgumentException();
        if (value.isJsonObject()) {
            if (value.getAsJsonObject().size() > 128) throw new IllegalArgumentException();
            for (var entry : value.getAsJsonObject().entrySet()) {
                if (entry.getKey().length() > 256) throw new IllegalArgumentException();
                bounded(entry.getValue(), depth + 1, budget);
            }
        } else if (value.isJsonArray()) {
            if (value.getAsJsonArray().size() > 512) throw new IllegalArgumentException();
            for (JsonElement item : value.getAsJsonArray()) bounded(item, depth + 1, budget);
        } else if (value.isJsonPrimitive()) {
            if (isString(value) && value.getAsString().codePointCount(0, value.getAsString().length()) > 262144) throw new IllegalArgumentException();
            if (value.getAsJsonPrimitive().isNumber() && new BigDecimal(value.getAsString()).abs().compareTo(new BigDecimal("1e30")) > 0)
                throw new IllegalArgumentException();
        }
    }

    private static boolean isString(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
    }

    private static boolean isInteger(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return false;
        try { return new BigDecimal(value.getAsString()).stripTrailingZeros().scale() <= 0; }
        catch (NumberFormatException invalid) { return false; }
    }

    private static boolean validId(JsonElement value) {
        return isString(value) || (isInteger(value) && Double.isFinite(value.getAsDouble()));
    }

    // Do not use JsonParser.parseString: Gson's compatibility parser accepts non-JSON input.
    // Stream recursively with explicit budgets so hostile nesting cannot exhaust the JVM stack.
    @SuppressWarnings("deprecation") // Gson 2.10 compatibility: newer Strictness API is unavailable there.
    public static JsonElement parse(String json) throws IOException {
        if (json == null || json.length() > 1048576) throw new LimitException();
        // Gson 2.10's non-lenient reader still permits raw control characters in strings.
        boolean quoted = false, escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (quoted && ch < 0x20) throw new IllegalArgumentException();
            if (escaped) { escaped = false; continue; }
            if (quoted && ch == '\\') escaped = true;
            else if (ch == '"') quoted = !quoted;
        }
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false); // Compatible with the project's Gson 2.10 baseline.
            JsonElement value = read(reader, 0, new int[]{8192});
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException();
            return value;
        }
    }

    private static JsonElement read(JsonReader reader, int depth, int[] budget) throws IOException {
        if (depth > 24 || --budget[0] < 0) throw new LimitException();
        switch (reader.peek()) {
            case BEGIN_OBJECT: {
                JsonObject value = new JsonObject();
                reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    if (value.has(key)) throw new IllegalArgumentException();
                    if (key.length() > 256 || value.size() >= 128) throw new LimitException();
                    value.add(key, read(reader, depth + 1, budget));
                }
                reader.endObject();
                return value;
            }
            case BEGIN_ARRAY: {
                JsonArray value = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) {
                    if (value.size() >= 512) throw new LimitException();
                    value.add(read(reader, depth + 1, budget));
                }
                reader.endArray();
                return value;
            }
            case STRING: {
                String value = reader.nextString();
                if (value.codePointCount(0, value.length()) > 262144) throw new LimitException();
                return new JsonPrimitive(value);
            }
            case NUMBER: {
                String number = reader.nextString();
                if (number.length() > 128) throw new LimitException();
                BigDecimal value = new BigDecimal(number);
                if (Math.abs((long) value.scale()) > 1000) throw new LimitException();
                return new JsonPrimitive(value);
            }
            case BOOLEAN: return new JsonPrimitive(reader.nextBoolean());
            case NULL: reader.nextNull(); return JsonNull.INSTANCE;
            default: throw new IllegalArgumentException();
        }
    }

    private static final class LimitException extends IllegalArgumentException { }

    public static JsonObject error(JsonElement id, int code, String message) {
        return Jsons.obj("jsonrpc", "2.0", "id", id, "error", Jsons.obj("code", code, "message", message));
    }
}
