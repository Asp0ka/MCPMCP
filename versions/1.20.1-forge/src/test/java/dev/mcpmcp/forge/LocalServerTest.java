package dev.mcpmcp.forge;

import com.google.gson.*;
import dev.mcpmcp.core.Jsons;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class LocalServerTest {
    private static int assertions;
    private static final String TOKEN="test-only-"+"x".repeat(64);
    private static final String BODY="{\"method\":\"status\",\"args\":{}}";
    private static final HttpClient CLIENT=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private static void check(boolean ok,String message){assertions++;if(!ok)throw new AssertionError(message);}
    private static HttpResponse<String> post(int port,String body,String...headers)throws Exception{
        HttpRequest.Builder b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/call")).timeout(Duration.ofSeconds(5)).POST(HttpRequest.BodyPublishers.ofString(body));
        for(int i=0;i<headers.length;i+=2)b.header(headers[i],headers[i+1]);
        return CLIENT.send(b.build(),HttpResponse.BodyHandlers.ofString());
    }
    private static String raw(int port,String headers)throws Exception{
        try(Socket socket=new Socket("127.0.0.1",port)){
            socket.setSoTimeout(4000);
            socket.getOutputStream().write(("POST /call HTTP/1.1\r\n"+headers+"Content-Length: "+BODY.length()+"\r\nConnection: close\r\n\r\n"+BODY).getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            return new String(socket.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        }
    }
    public static void main(String[]args)throws Exception{
        int port;
        AtomicReference<String> token=new AtomicReference<>(TOKEN);
        LocalServer first=new LocalServer(0,token::get,(m,a)->Jsons.obj("method",m,"data",a));
        try(first){
            port=first.port();
            check(port>0,"exposes actual ephemeral bound port");
            check(post(port,BODY,"Content-Type","application/json").statusCode()==401,"legacy auth");
            var ok=post(port,BODY,"Content-Type","application/json","Authorization","Bearer "+TOKEN);
            check(ok.statusCode()==200 && JsonParser.parseString(ok.body()).getAsJsonObject().get("method").getAsString().equals("status"),"legacy roundtrip");
            check(post(port,BODY,"Content-Type","application/json","Authorization","Bearer "+TOKEN,"Origin","https://example.org").statusCode()==403,"legacy origin");
            check(raw(port,"Host: localhost:"+port+"\r\nAuthorization: Bearer "+TOKEN+"\r\nContent-Type: application/json\r\n").startsWith("HTTP/1.1 403"),"exact loopback Host");
            String duplicateHost=raw(port,"Host: 127.0.0.1:"+port+"\r\nHost: evil.invalid\r\nAuthorization: Bearer "+TOKEN+"\r\nContent-Type: application/json\r\n");
            check(duplicateHost.startsWith("HTTP/1.1 403") || duplicateHost.startsWith("HTTP/1.1 400"),"duplicate Host denied by JDK or endpoint: "+duplicateHost.lines().findFirst().orElse("empty"));
            check(post(port," ".repeat(1048577),"Content-Type","application/json","Authorization","Bearer "+TOKEN).statusCode()==413,"legacy body bound");
            String deep="{\"method\":\"status\",\"args\":{\"deep\":"+"[".repeat(10000)+"0"+"]".repeat(10000)+"}}";
            check(post(port,deep,"Content-Type","application/json","Authorization","Bearer "+TOKEN).statusCode()==400,"legacy nesting cannot exhaust stack");
            check(raw(port,"Host: 127.0.0.1:"+port+"\r\nAuthorization: Bearer "+TOKEN+"\r\nAuthorization: Bearer wrong\r\nContent-Type: application/json\r\n").startsWith("HTTP/1.1 401"),"duplicate Authorization denied");
            check(post(port,"{\"method\":\"status\",\"args\":{},\"extra\":1}","Content-Type","application/json","Authorization","Bearer "+TOKEN).statusCode()==400,"legacy closed envelope");
            token.set("replacement-test-token");
            check(post(port,BODY,"Content-Type","application/json","Authorization","Bearer "+TOKEN).statusCode()==401,"token rotation invalidates old token");
            check(post(port,BODY,"Content-Type","application/json","Authorization","Bearer replacement-test-token").statusCode()==200,"token supplier is refreshed");
        }
        first.close();
        try(LocalServer restarted=new LocalServer(port,()->TOKEN,(m,a)->Jsons.obj("ok",true))){
            check(restarted.port()==port,"close permits same-port restart");
            check(post(port,BODY,"Content-Type","application/json","Authorization","Bearer "+TOKEN).statusCode()==200,"restarted endpoint works");
        }
        try(LocalServer failing=new LocalServer(0,()->TOKEN,(m,a)->{throw new IllegalStateException("private-message");})){
            var response=post(failing.port(),BODY,"Content-Type","application/json","Authorization","Bearer "+TOKEN);
            check(response.statusCode()==400 && !response.body().contains("private-message"),"legacy exception sanitized");
        }
        try(LocalServer large=new LocalServer(0,()->TOKEN,(m,a)->Jsons.obj("huge","x".repeat(12*1024*1024+1)))){
            var response=post(large.port(),BODY,"Content-Type","application/json","Authorization","Bearer "+TOKEN);
            check(response.statusCode()==413 && response.body().length()<1024,"legacy response bounded");
        }
        CountDownLatch entered=new CountDownLatch(1),interrupted=new CountDownLatch(1);
        LocalServer pending=new LocalServer(0,()->TOKEN,(m,a)->{
            entered.countDown();
            try{new CountDownLatch(1).await();}catch(InterruptedException stopped){interrupted.countDown();Thread.currentThread().interrupt();}
            return Jsons.error("stopped");
        });
        CompletableFuture<?> pendingRequest=CLIENT.sendAsync(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+pending.port()+"/call")).timeout(Duration.ofSeconds(5)).header("Authorization","Bearer "+TOKEN).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(BODY)).build(),HttpResponse.BodyHandlers.ofString());
        check(entered.await(3,TimeUnit.SECONDS),"request entered dispatcher");
        pending.close();
        check(interrupted.await(1,TimeUnit.SECONDS),"close interrupts pending dispatcher");
        pendingRequest.cancel(true);
        long workers=Thread.getAllStackTraces().keySet().stream().filter(t->t.isAlive()&&t.getName().equals("mcpmcp-http")).count();
        check(workers==0,"close releases HTTP worker threads");
        System.out.println("LocalServerTest: "+assertions+" assertions passed");
    }
}
