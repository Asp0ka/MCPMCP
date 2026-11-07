package dev.mcpmcp.forge;
import com.google.gson.*;
import com.sun.net.httpserver.*;
import dev.mcpmcp.core.*;
import java.net.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import java.util.concurrent.*;import java.util.function.*;
public final class LocalServer implements AutoCloseable {
 private final HttpServer server;private final ExecutorService pool;
 public LocalServer(int port,Supplier<String> token,BiFunction<String,JsonObject,JsonObject> call)throws Exception{
  server=HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),port),16);pool=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(32),r->{Thread t=new Thread(r,"mcpmcp-http");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());server.setExecutor(pool);
  server.createContext("/",e->{try(e){JsonObject out;int code=200;
   if(!("127.0.0.1:"+server.getAddress().getPort()).equals(e.getRequestHeaders().getFirst("Host"))||e.getRequestHeaders().containsKey("Origin")){send(e,403,Jsons.error("forbidden_host_or_origin"));return;}
   String auth=e.getRequestHeaders().getFirst("Authorization"),secret=token.get();if(auth==null||secret==null||!MessageDigest.isEqual(auth.getBytes(StandardCharsets.UTF_8),("Bearer "+secret).getBytes(StandardCharsets.UTF_8))){send(e,401,Jsons.error("unauthorized"));return;}
   if(!e.getRequestURI().getPath().equals("/call")||!e.getRequestMethod().equals("POST")){send(e,404,Jsons.error("unknown_endpoint"));return;}
   String type=e.getRequestHeaders().getFirst("Content-Type");if(type==null||!type.startsWith("application/json")){send(e,415,Jsons.error("json_required"));return;}
   byte[] body=e.getRequestBody().readNBytes(1048577);if(body.length>1048576){send(e,413,Jsons.error("request_too_large"));return;}
   try{JsonObject request=JsonParser.parseString(new String(body,StandardCharsets.UTF_8)).getAsJsonObject();if(!request.keySet().equals(java.util.Set.of("method","args")))throw new IllegalArgumentException("invalid_request");Schema.bounded(request);out=call.apply(Jsons.str(request,"method",""),Jsons.object(request,"args"));}
   catch(Exception ex){out=Jsons.obj("error","invalid_or_failed_request","reason",ex.getClass().getSimpleName()+": "+java.util.Objects.toString(ex.getMessage(),""));code=400;}send(e,code,out);
  }});server.start();
 }
 private static void send(HttpExchange e,int code,JsonObject value)throws java.io.IOException{byte[] bytes=Jsons.GSON.toJson(value).getBytes(StandardCharsets.UTF_8);if(bytes.length>12*1024*1024){bytes="{\"error\":\"response_too_large\"}".getBytes(StandardCharsets.UTF_8);code=413;}e.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");e.getResponseHeaders().set("Cache-Control","no-store");e.sendResponseHeaders(code,bytes.length);e.getResponseBody().write(bytes);}
 public void close(){server.stop(0);pool.shutdownNow();}
}
