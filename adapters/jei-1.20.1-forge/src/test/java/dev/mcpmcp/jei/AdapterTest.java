package dev.mcpmcp.jei;
import com.google.gson.*;
import dev.mcpmcp.api.*;
import java.util.*;

/** Public provider contract exercised through the real registered callbacks. */
public final class AdapterTest {
    static int checks;
    static void check(boolean ok,String name) { checks++;if(!ok)throw new AssertionError(name); }
    static void rejects(PluginContext.Handler h,String args) throws Exception { boolean rejected=false;try { h.call(JsonParser.parseString(args).getAsJsonObject()); } catch(IllegalArgumentException e) { rejected=true; }check(rejected,"invalid request rejected before backend"); }
    public static void main(String[] args)throws Exception {
        Map<String,PluginContext.Handler> reads=new LinkedHashMap<>(),actions=new LinkedHashMap<>();int[] calls={0};
        JeiProvider provider=new JeiProvider(new JeiProvider.Backend(){public JsonObject read(String k,JsonObject a){calls[0]++;JsonObject r=a.deepCopy();r.addProperty("kind",k);return r;}public JsonObject act(String k,JsonObject a){calls[0]++;JsonObject r=a.deepCopy();r.addProperty("action",k);return r;}});
        provider.register(new PluginContext(){
            public void action(String n,JsonObject schema,String transport,String permission,Handler h){check(transport.equals("input")&&permission.equals("normal"),"native handler input policy");actions.put(n,h);dev.mcpmcp.core.PluginValidation.schema(schema);}
            public void observer(String k,int p,Handler h){reads.put(k,h);}
            public JsonObject execute(String k,JsonObject a){throw new AssertionError("no core action during registration");}public JsonObject observe(String k,JsonObject a){throw new AssertionError("no recursive core observer");}
        });
        check(provider.id().equals("mcpmcp.jei"),"provider id");check(reads.size()==6&&actions.size()==2,"all eight hooks registered");check(!reads.containsKey("recipes"),"core recipe provider preserved");
        check(reads.get("jei").call(new JsonObject()).get("kind").getAsString().equals("jei"),"status discoverable");
        for(String k:List.of("jei.categories","jei.ingredients","jei.recipes")){
            String base=k.equals("jei.recipes")?"\"category\":\"minecraft:crafting\",":"";
            rejects(reads.get(k),"{"+base+"\"limit\":0}");rejects(reads.get(k),"{"+base+"\"limit\":65}");rejects(reads.get(k),"{"+base+"\"scan_budget\":2049}");rejects(reads.get(k),"{"+base+"\"unknown\":true}");
        }
        rejects(reads.get("jei.recipes"),"{}");rejects(reads.get("jei.recipe"),"{}");rejects(reads.get("jei.transfer"),"{}");rejects(actions.get("jei.transfer"),"{\"handle\":\"x\",\"dry_run\":\"false\"}");
        int before=calls[0];JsonObject valid=JsonParser.parseString("{\"handle\":\"opaque-id\",\"dry_run\":false}").getAsJsonObject();
        check(actions.get("jei.transfer").call(valid).get("handle").getAsString().equals("opaque-id")&&calls[0]==before+1,"action forwards exactly once");
        QueryStoreTest.run();System.out.println("JEI Adapter contracts: "+checks+" checks passed");
    }
}
