package dev.mcpmcp.core;
import com.google.gson.*;
import java.util.*;
public final class CoreTest {
 static int count; static void ok(boolean v){count++;if(!v)throw new AssertionError("assertion "+count);}
 static void fails(Runnable r){try{r.run();throw new AssertionError("expected rejection");}catch(IllegalArgumentException expected){count++;}}
 public static void main(String[] args)throws Exception {
  ok(Policy.allowed("NORMAL","MIXED","packet","normal"));
  ok(!Policy.allowed("NORMAL","MIXED","packet","cheat"));
  ok(!Policy.allowed("CHEAT","MIXED","packet","hack"));
  ok(Policy.allowed("HACK_CHEAT","PACKET","packet","cheat"));
  ok(!Policy.allowed("HACK","PACKET","input","normal"));
  ok(!Policy.allowed("NORMAL","SIMULATE","packet","normal"));
  ok(Policy.allowed("NORMAL","SIMULATE","client","normal"));
  ok(!Policy.allowed("NORMAL","PACKET","baritone","normal"));
  ok(!Policy.allowed("BAD","MIXED","packet","normal"));
  fails(()->Bounds.of(Jsons.obj("min_x",0,"min_y",0,"min_z",0,"max_x",100000,"max_y",100000,"max_z",100000),16777216));
  Bounds b=Bounds.of(Jsons.obj("min_x",-1,"min_y",0,"min_z",2,"max_x",1,"max_y",1,"max_z",3),100);
  ok(b.volume()==12);ok(Arrays.equals(b.position(0),new int[]{-1,0,2}));ok(Arrays.equals(b.position(11),new int[]{1,1,3}));
  fails(()->Jsons.integer(Jsons.obj("n",true),"n",0));fails(()->Jsons.number(Jsons.obj("n","2"),"n",0));
  Schema.validate(Jsons.obj("value",2),JsonParser.parseString("{\"type\":\"object\",\"properties\":{\"value\":{\"type\":\"integer\",\"minimum\":0,\"maximum\":3}},\"required\":[\"value\"],\"additionalProperties\":false}").getAsJsonObject());count++;
  fails(()->Schema.validate(Jsons.obj("other",1),JsonParser.parseString("{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}").getAsJsonObject()));
  Jobs jobs=new Jobs();int[] n={0};
  JsonObject a=jobs.submit("id","x",Jsons.obj("v",1),()->{n[0]++;return Jsons.obj("observed_success",true);},0,1000);
  ok(a.get("status").getAsString().equals("queued"));jobs.pump(0);ok(n[0]==1);ok(jobs.result("id").get("status").getAsString().equals("observed_success"));
  jobs.submit("id","x",Jsons.obj("v",1),()->{n[0]++;return new JsonObject();},0,1000);jobs.pump(0);ok(n[0]==1);
  ok(jobs.submit("id","x",Jsons.obj("v",2),()->new JsonObject(),0,1000).has("error"));
  jobs.submit("cancel","x",new JsonObject(),()->Jsons.obj("pending",true),0,1000);jobs.cancel("cancel");jobs.pump(0);ok(jobs.result("cancel").get("status").getAsString().equals("cancelled"));
  jobs.submit("world","x",new JsonObject(),()->Jsons.obj("pending",true),0,1000);jobs.pump(1);ok(jobs.result("world").get("status").getAsString().equals("cancelled"));
  SessionLease lease=new SessionLease(0,false);ok(lease.observe(0,false).equals("open"));lease.opened(4);ok(lease.observe(4,true).equals("operate"));ok(lease.finish(4).equals("close"));
  SessionLease reuse=new SessionLease(7,true);ok(reuse.observe(7,true).equals("operate"));ok(reuse.finish(7).equals("keep"));ok(reuse.observe(8,true).equals("conflict"));
  System.out.println("CoreTest: "+count+" checks passed");
 }
}
