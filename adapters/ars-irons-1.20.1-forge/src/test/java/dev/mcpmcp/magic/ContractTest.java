package dev.mcpmcp.magic;

import com.google.gson.*;
import dev.mcpmcp.api.PluginContext;
import dev.mcpmcp.core.*;
import java.util.*;
import static dev.mcpmcp.core.Jsons.*;

/** Standalone contract tests run by check; never launches Minecraft. */
public final class ContractTest {
    private static int assertions;
    private static void check(boolean b,String why){assertions++;if(!b)throw new AssertionError(why);}
    private static void rejects(Runnable run,String why){try{run.run();throw new AssertionError(why);}catch(IllegalArgumentException expected){assertions++;}}
    private static final class Fake implements PluginContext {
        final Map<String,Handler> observers=new HashMap<>(),actions=new HashMap<>();
        final Map<String,JsonObject> schemas=new HashMap<>();
        public void observer(String k,int priority,Handler h){check(priority==100,"priority");check(observers.put(k,h)==null,"unique observer");}
        public void action(String n,JsonObject s,String t,String p,Handler h){check(t.equals("input")&&p.equals("normal"),"normal input policy");schemas.put(n,s);check(actions.put(n,h)==null,"unique action");}
        public JsonObject execute(String n,JsonObject a){return obj("action_id","child","status","queued");}
        public JsonObject observe(String k,JsonObject a){return obj("test_only",true);}
    }
    public static void main(String[] args)throws Exception {
        for(String mod:List.of("ars_nouveau","irons_spellbooks","traveloptics")) {
            int[] reads={0},acts={0};
            MagicProvider.Backend backend=new MagicProvider.Backend(){public JsonObject read(String m,JsonObject a){reads[0]++;return obj("mod",m,"args",a);}public JsonObject act(String m,String n,JsonObject a,PluginContext c){acts[0]++;check(!a.has("plugin_revision"),"revision removed");return c.execute("ui.key",obj("key",65));}};
            Fake f=new Fake();MagicProvider p=new MagicProvider(mod,backend);p.register(f);
            check(p.id().equals("mcpmcp."+mod),"id");
            JsonObject discovery=f.observers.get(mod).call(obj());
            check(discovery.has("schema")&&discovery.has("examples")&&discovery.has("unsupported"),"discoverable contract");check(reads[0]==0,"describe no client access");
            if(!mod.equals("ars_nouveau"))check(discovery.has("cast_submission")&&discovery.getAsJsonObject("cast_submission").get("entrypoint").getAsString().equals("ClientInputEvents.onClientTick"),"cast must use one native mod handler, independent of global key conflicts");
            for(JsonElement ex:discovery.getAsJsonArray("examples"))Schema.validate(ex.getAsJsonObject().get("arguments"),MagicProvider.observerSchema());
            f.observers.get(mod).call(obj("view","player"));check(reads[0]==1,"player delegated");
            rejects(()->Schema.validate(obj("view","invented"),MagicProvider.observerSchema()),"unknown view");
            rejects(()->Schema.validate(obj("server",true),MagicProvider.observerSchema()),"server access rejected");
            for(JsonObject invalid:List.of(obj("limit",0),obj("limit",33),obj("limit",1.5),obj("scan_budget",2049),obj("cursor",17)))rejects(()->Schema.validate(invalid,MagicProvider.observerSchema()),"bounded observer");
            String action=mod.equals("ars_nouveau")?"next_spell":"cast";
            JsonObject valid=mod.equals("ars_nouveau")?obj("expected_item","ars_nouveau:novice_spell_book"):obj("expected_spell",mod+":spell","expected_source","mainhand","expected_source_index",0);
            valid.addProperty("plugin_revision","test");f.actions.get(mod+"."+action).call(valid);check(acts[0]==1,"handler invoked once");
            if(!mod.equals("ars_nouveau")){
                for(boolean dryRun:List.of(false,true)){
                    JsonObject invalid=valid.deepCopy();invalid.addProperty("dry_run",dryRun);
                    try{f.actions.get(mod+"."+action).call(invalid);throw new AssertionError("dry_run must not silently cast");}catch(IllegalArgumentException expected){check(acts[0]==1,"unsupported dry_run rejected before backend execution");}
                }
                check(Policy.allowed("NORMAL","SIMULATE","input","normal")&&Policy.allowed("NORMAL","MIXED","input","normal"),"native cast retains normal input transports");
            }
            rejects(()->Schema.validate(obj(),f.schemas.get(mod+"."+action)),"identity guards required");
            check(!f.actions.containsKey(mod+".craft"),"no fake machine crafting");
            for(String name:f.actions.keySet())check(!Policy.allowed("NORMAL","PACKET","input","normal"),"packet transport cannot run input action");
        }
        Pages pages=new Pages();Object world=new Object(),owner=new Object();List<Integer> all=List.of(0,1,2,3,4);
        JsonObject a=obj("view","recipes","limit",1,"scan_budget",2);
        JsonObject first=pages.take(world,owner,a,all,o->(int)o==4,o->obj("value",o));
        check(first.getAsJsonArray("entries").isEmpty()&&!first.get("complete").getAsBoolean(),"empty incomplete page");
        String old=first.get("next_cursor").getAsString();a.addProperty("cursor",old);
        JsonObject second=pages.take(world,owner,a,all,o->(int)o==4,o->obj("value",o));check(second.get("examined_total").getAsInt()==4,"cursor advances by candidates");
        check(pages.take(world,owner,a,all,o->true,o->obj()).has("error"),"single-use cursor");
        a.addProperty("cursor",second.get("next_cursor").getAsString());JsonObject finalPage=pages.take(world,owner,a,all,o->(int)o==4,o->obj("value",o));
        check(finalPage.get("complete").getAsBoolean()&&finalPage.getAsJsonArray("entries").get(0).getAsJsonObject().get("value").getAsInt()==4,"final match after empty pages");
        a.remove("cursor");JsonObject next=pages.take(world,owner,a,all,o->true,o->obj());a.addProperty("cursor",next.get("next_cursor").getAsString());
        check(pages.take(new Object(),owner,a,all,o->true,o->obj()).has("error"),"world change invalidates");
        a.remove("cursor");next=pages.take(world,owner,a,all,o->true,o->obj());a.addProperty("cursor",next.get("next_cursor").getAsString());
        check(pages.take(world,new Object(),a,all,o->true,o->obj()).has("error"),"reload owner invalidates");
        a.remove("cursor");next=pages.take(world,owner,a,all,o->true,o->obj());a.addProperty("cursor",next.get("next_cursor").getAsString());a.addProperty("query","changed");
        check(pages.take(world,owner,a,all,o->true,o->obj()).has("error"),"filter mutation invalidates");
        Jobs jobs=new Jobs();int[] send={0};jobs.submit("once","key",obj(),()->{send[0]++;return obj("status","submitted");},1,10000);jobs.pump(1);jobs.pump(1);check(send[0]==1,"core job sends once");
        jobs.submit("cancel","key",obj(),()->{send[0]++;return obj();},1,10000);jobs.cancel("cancel");jobs.pump(1);check(send[0]==1,"cancel before execution sends nothing");
        jobs.submit("epoch","key",obj(),()->{send[0]++;return obj();},1,10000);jobs.pump(2);check(send[0]==1,"world change cancels queued input");
        System.out.println("ContractTest PASS assertions="+assertions+"; fixtures only; no game execution");
    }
}
