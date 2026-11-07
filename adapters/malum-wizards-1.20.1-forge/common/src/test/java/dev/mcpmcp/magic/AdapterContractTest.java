package dev.mcpmcp.magic;

import com.google.gson.*;
import dev.mcpmcp.api.*;
import dev.mcpmcp.core.Schema;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static dev.mcpmcp.core.Jsons.*;

/** Independent no-game regression tests, invoked by each project's check task. */
public final class AdapterContractTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void rejects(Runnable r,String message){boolean rejected=false;try{r.run();}catch(IllegalArgumentException expected){rejected=true;}check(rejected,message);}
    private static final class Context implements PluginContext {
        final Map<String,Handler> observers=new LinkedHashMap<>(),actions=new LinkedHashMap<>();
        public void observer(String kind,int priority,Handler h){check(priority==100,"priority");check(observers.put(kind,h)==null,"unique observer");}
        public void action(String n,JsonObject s,String t,String p,Handler h){check(t.equals("client")&&p.equals("normal"),"no packet or elevated permission");check(actions.put(n,h)==null,"unique action");}
        public JsonObject execute(String n,JsonObject a){throw new AssertionError("unexpected delegation");}
        public JsonObject observe(String n,JsonObject a){throw new AssertionError("recursive observation");}
    }
    public static void main(String[] args)throws Exception {
        String mod=args[0],providerClass=mod.equals("malum")?"dev.mcpmcp.malum.MalumProvider":"dev.mcpmcp.wizards.WizardsProvider";
        int[] reads={0},acts={0};MagicProvider.Backend fake=new MagicProvider.Backend(){
            public JsonObject read(String p,JsonObject a){reads[0]++;return obj("data",obj("part",p));}
            public JsonObject interact(JsonObject a){acts[0]++;return obj("delivery","submitted","server_acceptance_confirmed",false);}
        };
        McpmcpPlugin provider=(McpmcpPlugin)Class.forName(providerClass).getConstructor(MagicProvider.Backend.class).newInstance(fake);
        check(provider.id().equals("mcpmcp."+mod),"provider ID");Context ctx=new Context();provider.register(ctx);
        check(ctx.observers.size()==6&&ctx.actions.size()==1,"isolated registrations");
        check(ctx.observers.keySet().stream().allMatch(k->k.equals(mod)||k.startsWith(mod+".")),"mod-only observers");
        JsonObject root=ctx.observers.get(mod).call(obj());check(root.getAsJsonObject("contract").getAsJsonObject("contracts").size()==7,"discoverable all schemas");
        ctx.observers.get(mod+".recipes").call(obj("limit",1,"scan_budget",1));
        ctx.observers.get(mod+".blocks").call(obj("x",0,"y",64,"z",0,"radius",8));
        for(JsonObject invalid:List.of(obj("radius",9,"x",0,"y",64,"z",0),obj("x",0.5,"y",64,"z",0),obj("x",0,"z",0),obj("x",0,"y",64,"z",0,"server",true)))
            rejects(()->Schema.validate(invalid,MagicContracts.schema("blocks")),"closed bounded local schema");
        rejects(()->Schema.validate(obj("limit",0),MagicContracts.schema("recipes")),"zero page size");
        rejects(()->Schema.validate(obj("scan_budget",1025),MagicContracts.schema("recipes")),"bounded scan");
        JsonObject action=obj("x",0,"y",64,"z",0,"dimension","minecraft:overworld","expected_block",mod+":test","expected_item","minecraft:air","expected_count",0,"expected_nbt","");
        ctx.actions.get(mod+".interact").call(action);check(acts[0]==1,"exact one interaction");
        JsonObject missing=action.deepCopy();missing.remove("expected_nbt");rejects(()->Schema.validate(missing,MagicContracts.schema("interact")),"NBT precondition required");
        JsonObject injection=action.deepCopy();injection.addProperty("method","startRitual");rejects(()->Schema.validate(injection,MagicContracts.schema("interact")),"arbitrary method rejected");
        // Check the registered handlers enforce schemas before reaching even the fake backend.
        int before=reads[0];try{ctx.observers.get(mod+".recipes").call(obj("limit",100));throw new AssertionError("invalid handler accepted");}catch(IllegalArgumentException expected){}
        check(reads[0]==before,"invalid observer must not read");
        try{ctx.actions.get(mod+".interact").call(missing);throw new AssertionError("invalid action accepted");}catch(IllegalArgumentException expected){}check(acts[0]==1,"invalid action must not execute");
        AtomicLong clock=new AtomicLong(100);PageTokens tokens=new PageTokens(clock::get);Object world=new Object();
        check(tokens.resolve("",world,"q")==0,"first page");String token=tokens.next(world,"q",17);check(tokens.resolve(token,world,"q")==17,"exact continuation");
        rejects(()->tokens.resolve(token,new Object(),"q"),"world changed");rejects(()->tokens.resolve(token,world,"changed"),"query changed");
        clock.set(120100);rejects(()->tokens.resolve(token,world,"q"),"expired cursor");
        String oldest=tokens.next(world,"q",0);for(int i=0;i<64;i++)tokens.next(world,"q",i);rejects(()->tokens.resolve(oldest,world,"q"),"bounded cursor memory");
        boolean[] ok=new boolean[13];Arrays.fill(ok,true);check(InteractionGuard.rejection(state(ok))==null,"valid preflight");
        String[] reasons={"not_in_world","dimension_changed","close_existing_screen_or_container_first","close_existing_screen_or_container_first","cursor_not_empty","player_not_allowed_to_interact","player_not_allowed_to_interact","target_unloaded_or_outside_world","unsupported_work_block_entity","target_block_changed","held_item_changed","held_item_changed","held_item_changed"};
        for(int i=0;i<ok.length;i++){boolean[] bad=ok.clone();bad[i]=false;check(reasons[i].equals(InteractionGuard.rejection(state(bad))),"guard reason "+i);}
        for(String field:List.of("face","hand")){JsonObject bad=action.deepCopy();bad.addProperty(field,"arbitrary");rejects(()->Schema.validate(bad,MagicContracts.schema("interact")),"invalid native enum");}
        JsonObject wrongType=action.deepCopy();wrongType.addProperty("expected_count","0");rejects(()->Schema.validate(wrongType,MagicContracts.schema("interact")),"count type");
        check(!MagicContracts.discovery(mod).get("automatic_rituals").getAsBoolean(),"no invented automatic rituals");
        String service="META-INF/services/dev.mcpmcp.api.McpmcpPlugin";try(var in=AdapterContractTest.class.getClassLoader().getResourceAsStream(service)){
            check(in!=null,"service exists");String text=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).trim();check(text.equals(providerClass),"exactly own service provider");}
        System.out.println(mod+": "+checks+" checks passed (no Minecraft launched; live effects untested)");
    }
    private static InteractionGuard.State state(boolean[] s){return new InteractionGuard.State(s[0],s[1],s[2],s[3],s[4],s[5],s[6],s[7],s[8],s[9],s[10],s[11],s[12]);}
}
