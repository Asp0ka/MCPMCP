package dev.mcpmcp.core;

import com.google.gson.*;
import java.util.function.BiFunction;

/** Standalone Java 17 contract test; no game or Gradle required. */
public final class McpProtocolTest {
    private static int assertions;
    private static McpProtocol protocol;
    private static JsonObject request(String text) throws Exception {
        return protocol.handle(text);
    }
    private static void check(boolean ok, String message) {
        assertions++;
        if (!ok) throw new AssertionError(message);
    }
    private static void error(String json, int code) throws Exception {
        JsonObject result = request(json);
        check(result != null && result.has("error") && result.getAsJsonObject("error").get("code").getAsInt() == code, "expected " + code + " got " + result + " for " + json.substring(0, Math.min(256, json.length())));
    }
    private static void malformedAndVersions() throws Exception {
        error("{", -32700);
        check(request("{").has("id") && request("{").get("id").isJsonNull(), "unidentified errors carry null id");
        error("{jsonrpc:'2.0',id:1,method:'ping'}", -32700);
        error("{\"jsonrpc\":\"2.0\",\"id\":1,\"id\":2,\"method\":\"ping\"}", -32700);
        error("[]", -32600);
        error("null", -32600);
        error("{\"jsonrpc\":\"2.0\",\"id\":1e9999999999,\"method\":\"ping\"}", -32700);
        error("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\",\"params\":{\"x\":\"raw\nline\"}}", -32700);
        error("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":42}", -32600);
        check(request("{\"jsonrpc\":\"2.0\",\"id\":9007199254740993,\"method\":\"ping\"}").get("id").getAsString().equals("9007199254740993"),"large numeric ID preserved exactly");
        error("{\"jsonrpc\":\"2.0\",\"id\":"+"9".repeat(1000)+",\"method\":\"ping\"}", -32600);
        error("{\"id\":1,\"method\":\"ping\"}", -32600);
        for (String id : new String[]{"null", "true", "{}", "[]", "1.5"})
            error("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"ping\"}", -32600);
        error("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\",\"params\":[]}", -32602);
        error("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"not-a-method\"}", -32601);
        error("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}", -32602);
        error("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"notifications/initialized\"}", -32601);
        check(request("{\"jsonrpc\":\"2.0\",\"id\":9,\"result\":{}}") == null, "client result accepted");
        check(request("{\"jsonrpc\":\"2.0\",\"id\":9,\"error\":{\"code\":-1,\"message\":\"client error\"}}") == null, "client error accepted");
        error("{\"jsonrpc\":\"2.0\",\"id\":9,\"result\":{},\"error\":{\"code\":-1,\"message\":\"x\"}}", -32600);
        error("{\"jsonrpc\":\"2.0\",\"id\":9,\"error\":{\"code\":true,\"message\":\"x\"}}", -32600);
        String deep = "[".repeat(1000) + "0" + "]".repeat(1000);
        error(deep, -32600);
        for (String version : new String[]{"2025-11-25", "2025-06-18", "2025-03-26", "future-version"}) {
            JsonObject init = request("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\""+version+"\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}");
            check(init.getAsJsonObject("result").get("protocolVersion").getAsString().equals(version.equals("future-version") ? "2025-11-25" : version), "version negotiation "+version);
        }
        error("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}", -32602);
        check(request("{\"jsonrpc\":\"2.0\",\"method\":\"tools/call\",\"params\":{\"name\":\"execute_action\",\"arguments\":{\"action\":\"anything\"}}}") == null, "tool notification cannot execute");
    }
    private static JsonObject tool(String name, JsonObject arguments) throws Exception {
        return request(Jsons.GSON.toJson(Jsons.obj("jsonrpc", "2.0", "id", 42, "method", "tools/call", "params", Jsons.obj("name", name, "arguments", arguments))));
    }
    private static JsonObject textData(JsonObject response) {
        return JsonParser.parseString(response.getAsJsonObject("result").getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString()).getAsJsonObject();
    }
    private static void tools() throws Exception {
        BiFunction<String, JsonObject, JsonObject> dispatch = (method, a) -> Jsons.obj("method", method, "args", a);
        protocol = new McpProtocol(dispatch);
        JsonObject listed = request("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}");
        check(listed.has("result"), "built-in tools/list");
        JsonArray tools = listed.getAsJsonObject("result").getAsJsonArray("tools");
        check(tools.size() == 19, "all bridge tools");
        java.util.Map<String,String> aliases = java.util.Map.of("get_context","context","get_player_state","player","get_inventory","inventory","get_screen","screen","get_recipes","recipes","get_chat_messages","chat","get_mods","mods","get_paths","paths");
        java.util.Map<String,String> methods = java.util.Map.of("get_status","status","list_capabilities","capabilities","describe_action","describe","execute_action","execute","get_job","job","cancel_job","cancel","get_plugins","plugins","reload_plugins","plugins.reload","read_plugin_file","plugins.read","write_plugin_file","plugins.write");
        for (JsonElement value : tools) {
            JsonObject declaration = value.getAsJsonObject();
            String name = declaration.get("name").getAsString();
            check(!declaration.get("description").getAsString().isBlank(), "description "+name);
            check(!declaration.getAsJsonObject("inputSchema").get("additionalProperties").getAsBoolean(), "closed schema "+name);
            JsonObject a = new JsonObject();
            for (JsonElement key : declaration.getAsJsonObject("inputSchema").getAsJsonArray("required"))
                a.addProperty(key.getAsString(), key.getAsString().equals("text") ? "" : "example");
            JsonObject response = tool(name, a);
            check(response.has("result") && !response.getAsJsonObject("result").get("isError").getAsBoolean(), "tool dispatch "+name);
            JsonObject data = textData(response), sent = data.getAsJsonObject("args");
            check(data.get("method").getAsString().equals(aliases.containsKey(name) ? "observe" : methods.getOrDefault(name,name)), "method mapping "+name);
            if (aliases.containsKey(name)) {
                check(sent.get("kind").getAsString().equals(aliases.get(name)), "alias kind "+name);
                check(sent.getAsJsonObject("arguments").equals(a), "alias arguments "+name);
            } else if (name.equals("execute_action")) {
                check(sent.get("action_id").getAsString().matches("[A-Za-z0-9_.-]{1,96}"), "generated action_id");
                check(!a.has("action_id"), "do not mutate caller arguments");
            } else check(sent.equals(a), "arguments mapping "+name);
        }
        check(textData(tool("execute_action", Jsons.obj("action", "example", "action_id", "keep-this"))).getAsJsonObject("args").get("action_id").getAsString().equals("keep-this"), "action ID preserved");
        for (JsonObject invalid : new JsonObject[]{Jsons.obj("action", " "), Jsons.obj("action","x","action_id","bad id"), Jsons.obj("action","x","unexpected",1), new JsonObject()})
            check(tool("execute_action", invalid).getAsJsonObject("error").get("code").getAsInt()==-32602, "invalid execute args");
        check(tool("missing",new JsonObject()).getAsJsonObject("error").get("code").getAsInt()==-32602, "unknown tool");
        check(tool("get_inventory",Jsons.obj("detail","unknown")).has("error"), "enum validation");
        check(tool("get_recipes",Jsons.obj("limit",1.5)).has("error"), "integer validation");
        check(tool("get_chat_messages",Jsons.obj("limit",true)).has("error"), "boolean is not integer");
        check(tool("get_chat_messages",Jsons.obj("after",9007199254740992L)).has("error"), "integer maximum");
        check(tool("write_plugin_file",Jsons.obj("path","notes.md","text","x".repeat(262144))).has("result"), "bridge maximum plugin text");
        check(tool("write_plugin_file",Jsons.obj("path","notes.md","text","😀".repeat(140000))).has("result"), "schema lengths count Unicode code points");
        JsonObject nested=new JsonObject(),argumentNode=nested;
        for(int i=0;i<14;i++){JsonObject child=new JsonObject();argumentNode.add("child",child);argumentNode=child;}
        check(tool("observe",Jsons.obj("kind","example","arguments",nested)).has("result"), "message envelope does not consume argument depth allowance");
        BiFunction<String, JsonObject, JsonObject> failed = (m,a) -> Jsons.error("business_failure");
        protocol = new McpProtocol(failed);
        check(tool("get_status",new JsonObject()).getAsJsonObject("result").get("isError").getAsBoolean(), "business errors are tool errors");
        BiFunction<String, JsonObject, JsonObject> nestedError = (m,a) -> Jsons.obj("data",Jsons.error("plugin_failure"));
        protocol = new McpProtocol(nestedError);
        check(tool("get_status",new JsonObject()).getAsJsonObject("result").get("isError").getAsBoolean(), "wrapped business errors are tool errors");
        BiFunction<String, JsonObject, JsonObject> screenshot = (m,a) -> Jsons.obj("data",Jsons.obj("base64","aGVsbG8=","mime","image/png"));
        protocol = new McpProtocol(screenshot);
        JsonObject image = tool("observe",Jsons.obj("kind","screenshot")).getAsJsonObject("result").getAsJsonArray("content").get(0).getAsJsonObject();
        check(image.get("type").getAsString().equals("image") && image.get("data").getAsString().equals("aGVsbG8=") && image.get("mimeType").getAsString().equals("image/png"), "MCP image result");
        BiFunction<String, JsonObject, JsonObject> throwsError = (m,a) -> { throw new IllegalStateException("private-exception-must-not-leak"); };
        protocol = new McpProtocol(throwsError);
        JsonObject failure = tool("execute_action",Jsons.obj("action","example","action_id","retry-this"));
        check(failure.getAsJsonObject("result").get("isError").getAsBoolean(), "dispatcher failure is tool error");
        JsonObject failureData = textData(failure);
        check(failureData.get("delivery_unknown").getAsBoolean() && failureData.get("action_id").getAsString().equals("retry-this"), "retain action identity on uncertain delivery");
        check(!failure.toString().contains("private-exception"), "no exception leakage");
        BiFunction<String,JsonObject,JsonObject> timeout = (m,a) -> Jsons.error("client_thread_timeout");
        protocol = new McpProtocol(timeout);
        JsonObject timedOut = textData(tool("execute_action",Jsons.obj("action","example")));
        check(timedOut.has("action_id") && timedOut.get("delivery_unknown").getAsBoolean(), "generated action ID survives client queue timeout");
        BiFunction<String,JsonObject,JsonObject> tooLarge = (m,a) -> Jsons.obj("huge","x".repeat(12*1024*1024+1));
        protocol = new McpProtocol(tooLarge);
        JsonObject oversized = tool("get_status",new JsonObject());
        check(oversized.getAsJsonObject("result").get("isError").getAsBoolean() && oversized.toString().length()<1024,"oversized tool output is a bounded error");
        BiFunction<String,JsonObject,JsonObject> deepOutput = (m,a) -> {
            JsonObject root=new JsonObject(),node=root;
            for(int i=0;i<100;i++){JsonObject child=new JsonObject();node.add("data",child);node=child;}
            return root;
        };
        protocol = new McpProtocol(deepOutput);
        check(tool("get_status",new JsonObject()).getAsJsonObject("result").get("isError").getAsBoolean(),"bounded output nesting");
        BiFunction<String,JsonObject,JsonObject> quoteHeavy = (m,a) -> Jsons.obj("text","\"".repeat(4*1024*1024));
        protocol = new McpProtocol(quoteHeavy);
        JsonObject quoteResult=tool("execute_action",Jsons.obj("action","example"));
        JsonObject quoteData=textData(quoteResult);
        check(quoteResult.getAsJsonObject("result").get("isError").getAsBoolean() && quoteData.has("action_id") && quoteData.get("delivery_unknown").getAsBoolean(),"final MCP envelope output cap retains action identity");
        BiFunction<String,JsonObject,JsonObject> malformedImage = (m,a) -> Jsons.obj("base64","aGVsbG8=","mime",7);
        protocol = new McpProtocol(malformedImage);
        check(tool("observe",Jsons.obj("kind","screenshot")).getAsJsonObject("result").get("isError").getAsBoolean(),"malformed image is tool failure, not invalid params");
    }
    public static void main(String[] args) throws Exception {
        BiFunction<String, JsonObject, JsonObject> call = (method, a) -> {
            throw new AssertionError("Handshake must not invoke game dispatch");
        };
        protocol = new McpProtocol(call);
        JsonObject init = request("{\"jsonrpc\":\"2.0\",\"id\":\"init\",\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},\"clientInfo\":{\"name\":\"contract-test\",\"version\":\"1\"}}}");
        check(init.get("id").getAsString().equals("init"), "initialize echoes ID");
        JsonObject result = init.getAsJsonObject("result");
        check(result.get("protocolVersion").getAsString().equals("2025-11-25"), "negotiated version");
        check(result.getAsJsonObject("capabilities").keySet().equals(java.util.Set.of("tools")), "only tools advertised");
        check(!result.getAsJsonObject("capabilities").getAsJsonObject("tools").has("listChanged"), "no list-change support advertised");
        check(request("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"ping\"}").getAsJsonObject("result").size() == 0, "ping");
        check(request("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}") == null, "initialized notification");
        check(request("{\"jsonrpc\":\"2.0\",\"method\":\"ping\"}") == null, "ping notification never calls game");
        malformedAndVersions();
        tools();
        System.out.println("McpProtocolTest: " + assertions + " assertions passed");
    }
}
