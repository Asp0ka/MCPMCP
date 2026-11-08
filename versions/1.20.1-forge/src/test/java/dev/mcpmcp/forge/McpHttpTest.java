package dev.mcpmcp.forge;

import com.google.gson.*;
import dev.mcpmcp.core.Jsons;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/** Exercises real loopback HTTP sockets without loading Minecraft. */
public final class McpHttpTest {
    private static final String TOKEN = "test-only-token-" + "x".repeat(48);
    private static final String PING = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";
    private static int assertions;
    private static int port;
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private static void check(boolean ok, String message) {
        assertions++;
        if (!ok) throw new AssertionError(message);
    }
    private static HttpResponse<String> send(String method, String path, String body, String... headers) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(5));
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        for (int i=0;i<headers.length;i+=2) b.header(headers[i],headers[i+1]);
        return CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }
    private static HttpResponse<String> post(String body) throws Exception {
        return send("POST","/mcp",body,"Authorization","Bearer "+TOKEN,"Content-Type","application/json","Accept","application/json, text/event-stream");
    }
    private static JsonObject json(HttpResponse<String> response) {
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
    private static String initLegacy() {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}";
    }
    public static void main(String[] args) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (LocalServer server = new LocalServer(0,()->TOKEN,(method,a)-> {calls.incrementAndGet();return Jsons.obj("method",method,"args",a);})) {
            port = server.port();
            String nonce="a".repeat(64);
            HttpResponse<String> attested=send("GET","/identity?challenge="+nonce,null);
            check(attested.statusCode()==200,"unauthenticated listener proof endpoint");
            JsonObject identity=json(attested);
            check(identity.get("pid").getAsLong()==ProcessHandle.current().pid()&&identity.get("port").getAsInt()==port,"listener proof binds PID and port");
            check(java.util.UUID.fromString(identity.get("instance").getAsString()).toString().equals(identity.get("instance").getAsString()),"listener proof has canonical instance UUID");
            String proofText="mcpmcp-identity-v1\n"+identity.get("instance").getAsString()+"\n"+ProcessHandle.current().pid()+"\n"+port+"\n"+nonce;
            javax.crypto.Mac mac=javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(TOKEN.getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256"));
            String expected=java.util.HexFormat.of().formatHex(mac.doFinal(proofText.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            check(expected.equals(identity.get("proof").getAsString()),"listener proof validates without sending token");
            check(!attested.body().contains(TOKEN),"listener proof never returns token");
            check(send("GET","/identity?challenge=bad",null).statusCode()==400,"invalid listener challenge rejected");
            check(send("POST","/identity?challenge="+nonce,"{}").statusCode()==405,"identity endpoint read-only");
            check(send("GET","/identity?challenge="+nonce,null,"Origin","https://example.org").statusCode()==403,"identity retains Origin guard");
            HttpResponse<String> response = post(PING);
            check(response.statusCode()==200, "MCP endpoint responds to ping: HTTP "+response.statusCode());
            check(json(response).getAsJsonObject("result").size()==0, "ping result");
            check(calls.get()==0,"ping must not invoke game queue");
            check(send("POST","/mcp",PING,"Content-Type","application/json","Accept","application/json, text/event-stream").statusCode()==401,"MCP authentication required");
            check(send("POST","/mcp",PING,"Authorization","Bearer wrong","Content-Type","application/json","Accept","application/json, text/event-stream").statusCode()==401,"MCP wrong token");
            check(send("POST","/mcp",PING,"Authorization","Bearer "+TOKEN,"Origin","https://example.org").statusCode()==403,"MCP origin rejection");
            HttpResponse<String> get=send("GET","/mcp",null,"Authorization","Bearer "+TOKEN,"Accept","text/event-stream");
            check(get.statusCode()==405 && get.headers().firstValue("Allow").orElse("").contains("POST"),"GET 405 with Allow");
            check(send("DELETE","/mcp",null,"Authorization","Bearer "+TOKEN).statusCode()==405,"stateless DELETE 405");
            check(send("POST","/mcp",PING,"Authorization","Bearer "+TOKEN,"Content-Type","application/jsonp","Accept","application/json, text/event-stream").statusCode()==415,"exact JSON media type");
            check(send("POST","/mcp",PING,"Authorization","Bearer "+TOKEN,"Content-Type","application/json").statusCode()==406,"Accept required");
            for(String accept : new String[]{"application/json", "text/event-stream", "*/*", "application/json, text/event-stream;q=0", "application/json;q=NaN, text/event-stream"})
                check(send("POST","/mcp",PING,"Authorization","Bearer "+TOKEN,"Content-Type","application/json","Accept",accept).statusCode()==406,"invalid Accept "+accept);
            for(String version : new String[]{"2024-11-05","2030-01-01","invalid"})
                check(send("POST","/mcp",PING,"Authorization","Bearer "+TOKEN,"Content-Type","application/json","Accept","application/json, text/event-stream","MCP-Protocol-Version",version).statusCode()==400,"unsupported version "+version);
            for(String version : new String[]{"2025-11-25","2025-06-18","2025-03-26"})
                check(send("POST","/mcp",PING,"Authorization","Bearer "+TOKEN,"Content-Type","Application/JSON; charset=utf-8","Accept","application/json, text/event-stream","MCP-Protocol-Version",version).statusCode()==200,"supported version "+version);
            for(String body : new String[]{"{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}","{\"jsonrpc\":\"2.0\",\"method\":\"ping\"}","{\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{}}","{\"jsonrpc\":\"2.0\",\"id\":7,\"error\":{\"code\":-1,\"message\":\"x\"}}","{\"jsonrpc\":\"2.0\",\"method\":\"tools/call\",\"params\":{\"name\":\"execute_action\",\"arguments\":{\"action\":\"example\"}}}"}) {
                HttpResponse<String> accepted=post(body);
                check(accepted.statusCode()==202 && accepted.body().isEmpty(),"notification/client response 202 empty");
            }
            check(calls.get()==0,"notifications and handshake never call game");
            byte[] invalidUtf8 = "{\"jsonrpc\":\"2.0\",\"id\":\"X\",\"method\":\"ping\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            for(int i=0;i<invalidUtf8.length;i++) if(invalidUtf8[i]=='X'){invalidUtf8[i]=(byte)0xff;break;}
            HttpResponse<String> utf8 = CLIENT.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/mcp")).timeout(Duration.ofSeconds(5)).POST(HttpRequest.BodyPublishers.ofByteArray(invalidUtf8)).header("Authorization","Bearer "+TOKEN).header("Content-Type","application/json").header("Accept","application/json, text/event-stream").build(), HttpResponse.BodyHandlers.ofString());
            check(utf8.statusCode()==400,"reject malformed UTF-8");
            check(send("POST","/mcp",PING,"Authorization","Bearer "+TOKEN,"Content-Type","application/json; charset=iso-8859-1","Accept","application/json, text/event-stream").statusCode()==415,"MCP is UTF-8 only");
            check(send("GET","/mcp",null,"Authorization","Bearer "+TOKEN,"MCP-Protocol-Version","2024-11-05").statusCode()==400,"version validation also applies to GET");
            check(json(post(initLegacy())).getAsJsonObject("error").get("code").getAsInt()==-32602,"old initialize transport rejected");
            check(post(" ".repeat(1048577)).statusCode()==413,"1 MiB request bound");
            check(json(post("[]")).getAsJsonObject("error").get("code").getAsInt()==-32600,"batch rejected");
            check(json(post("{")).getAsJsonObject("error").get("code").getAsInt()==-32700,"parse error");
            JsonObject parsedError = json(post("{"));
            check(parsedError.has("id") && parsedError.get("id").isJsonNull(),"wire parse-error includes null ID");
            check(post("{\"jsonrpc\":\"2.0\",\"method\":\"ping\",\"params\":[]}").statusCode()==400,"malformed notification HTTP error");
            String init="{\"jsonrpc\":\"2.0\",\"id\":\"init\",\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}";
            HttpResponse<String> initialized=post(init);
            check(initialized.statusCode()==200 && json(initialized).getAsJsonObject("result").get("protocolVersion").getAsString().equals("2025-11-25"),"HTTP initialize");
            check(initialized.headers().firstValue("MCP-Session-Id").isEmpty(),"stateless: no session ID");
            check(initialized.headers().firstValue("Cache-Control").orElse("").equals("no-store"),"no cache");
            check(initialized.headers().firstValue("Content-Type").orElse("").startsWith("application/json"),"JSON response mode");
            check(send("POST","/mcp",PING,"Authorization","Bearer "+TOKEN,"Content-Type","application/json","Accept","application/json","Accept","text/event-stream").statusCode()==200,"multiple Accept headers combine as HTTP list");
            check(send("POST","/mcp",PING,"Authorization","Bearer "+TOKEN,"Content-Type","application/json","Accept","application/json, text/event-stream","MCP-Protocol-Version","2025-11-25","MCP-Protocol-Version","2024-11-05").statusCode()==400,"ambiguous version headers denied");
            check(send("POST","/mcp",PING,"Authorization","Bearer "+TOKEN,"Content-Type","application/json","Content-Type","text/plain","Accept","application/json, text/event-stream").statusCode()==415,"ambiguous Content-Type denied");
            check(json(post("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}")).getAsJsonObject("result").getAsJsonArray("tools").size()==19,"HTTP tool catalog");
            JsonObject called=json(post("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"get_inventory\"}}"));
            JsonObject data=JsonParser.parseString(called.getAsJsonObject("result").getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString()).getAsJsonObject();
            check(data.get("method").getAsString().equals("observe") && data.getAsJsonObject("args").get("kind").getAsString().equals("inventory"),"HTTP dispatch mapping");
            check(calls.get()==1,"one game dispatch per tool call");
            check(send("POST","/mcp/extra",PING,"Authorization","Bearer "+TOKEN).statusCode()==404,"exact MCP path");
        }
        System.out.println("McpHttpTest: "+assertions+" assertions passed");
    }
}
