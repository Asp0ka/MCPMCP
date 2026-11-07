package dev.mcpmcp.magic;

import com.google.gson.*;
import dev.mcpmcp.api.*;
import dev.mcpmcp.core.Schema;
import java.util.*;
import static dev.mcpmcp.core.Jsons.*;

/** One provider per independently pinned mod. Does not override core observers. */
public class MagicProvider implements McpmcpPlugin {
    public interface Backend {
        JsonObject read(String mod, JsonObject args) throws Exception;
        JsonObject act(String mod, String action, JsonObject args, PluginContext context) throws Exception;
    }
    private final String mod;
    private final Backend backend;
    protected MagicProvider(String mod) { this(mod,new ClientBackend()); }
    public MagicProvider(String mod, Backend backend) { this.mod=Objects.requireNonNull(mod);this.backend=Objects.requireNonNull(backend); }
    public String id() { return "mcpmcp."+mod; }
    public void register(PluginContext ctx) {
        ctx.observer(mod,100,a->{Schema.validate(a,observerSchema());
            if(str(a,"view","describe").equals("describe"))return describe(mod);
            return backend.read(mod,a);
        });
        for(String action:actions(mod)) ctx.action(mod+"."+action,actionSchema(mod,action),"input","normal",a->{
            JsonObject clean=a.deepCopy();clean.remove("plugin_revision");
            Schema.validate(clean,actionSchema(mod,action));return backend.act(mod,action,clean,ctx);
        });
    }
    public static List<String> actions(String mod) {
        return mod.equals("ars_nouveau")?List.of("use","next_spell","previous_spell","open_book","interact"):List.of("cast","quick_cast","interact");
    }
    private static JsonObject object(JsonObject p,List<String> required,String description) {
        return obj("type","object","properties",p,"required",required,"additionalProperties",false,"description",description);
    }
    public static JsonObject observerSchema() {
        return object(obj(
            "view",obj("type","string","enum",List.of("describe","player","spells","recipes","machine")),
            "limit",obj("type","integer","minimum",1,"maximum",32,"description","Page size; default 16."),
            "scan_budget",obj("type","integer","minimum",1,"maximum",2048,"description","Candidates examined this call; default 256. Continue incomplete empty pages."),
            "cursor",obj("type","string","maxLength",128,"description","Opaque single-use next_cursor; keep view/query/type/limit/scan_budget unchanged. Expires in 60 seconds and on world/reload change."),
            "query",obj("type","string","maxLength",128,"description","Case-insensitive identifier substring."),
            "type",obj("type","string","maxLength",128,"description","Exact recipe type registry ID, not serializer or JEI category."),
            "x",coordinate(),"y",coordinate(),"z",coordinate()),List.of(),"Read describe first. Client-only snapshots; absent/server-only data is unknown. machine requires x,y,z and never loads chunks.");
    }
    private static JsonObject coordinate(){return obj("type","integer","minimum",-30000000,"maximum",30000000);}
    public static JsonObject actionSchema(String mod,String action) {
        JsonObject p=new JsonObject();List<String> req=new ArrayList<>();
        switch(action) {
            case "use","interact","next_spell","previous_spell","open_book" -> {
                p.add("expected_item",obj("type","string","minLength",3,"maxLength",256));req.add("expected_item");
                if(action.equals("use")||action.equals("interact"))p.add("hand",obj("type","string","enum",List.of("main","off")));
                if(action.equals("interact")) {p.add("face",obj("type","string","enum",List.of("up","down","north","south","east","west")));p.add("x",coordinate());p.add("y",coordinate());p.add("z",coordinate());
                    p.add("expected_block",obj("type","string","minLength",3,"maxLength",256));req.addAll(List.of("x","y","z","expected_block"));}
                if(action.equals("use")){p.add("expected_spell",obj("type","string","maxLength",256));req.add("expected_spell");}
            }
            case "cast","quick_cast" -> {
                p.add("expected_spell",obj("type","string","minLength",3,"maxLength",256));req.add("expected_spell");
                p.add("expected_source",obj("type","string","minLength",1,"maxLength",128));req.add("expected_source");
                p.add("expected_source_index",obj("type","integer","minimum",0,"maximum",511));req.add("expected_source_index");
                if(action.equals("quick_cast")){p.add("index",obj("type","integer","minimum",0,"maximum",31));req.add("index");}
            }
            default -> throw new IllegalArgumentException("unsupported_action");
        }
        return object(p,req,"One normal client input submission, input-capable transport required. Re-read observer and child core job; submission is not spell/craft success.");
    }
    public static JsonObject describe(String mod) {
        JsonObject schemas=obj("observer",observerSchema());JsonArray actionSpecs=new JsonArray();
        for(String a:actions(mod))actionSpecs.add(obj("name",mod+"."+a,"transport","input","permission","normal","input_schema",actionSchema(mod,a)));
        return obj("mod_id",mod,"source","target_jar_verified_client_api","schema",schemas,"actions",actionSpecs,
            "examples",List.of(obj("kind",mod,"arguments",obj()),obj("kind",mod,"arguments",obj("view","player")),
                obj("kind",mod,"arguments",obj("view","spells","limit",16)),obj("kind",mod,"arguments",obj("view","recipes","limit",16,"scan_budget",256)),
                obj("kind",mod,"arguments",obj("view","machine","x",0,"y",64,"z",0))),
            "scope","local_client_player; synchronized item spell containers; client RecipeManager; explicitly addressed loaded block entity",
            "unknown",List.of("server_only_resources","unloaded_chunks","server_acceptance_without_readback","recipe_rules_not_represented_in_client_RecipeManager"),
            "unsupported",List.of("arbitrary_capability_mutation","automatic_magic_workstation_crafting","generic_recipe_execution","arbitrary_spellbook_editing","Traveloptics_special_armor_weapon_abilities"),
            "machine_workflow","Observe local machine, inventory and actual menu. interact submits one core block.interact only; use core menu operations only after reading matching menu and slots.",
            "selection",mod.equals("ars_nouveau")?"next_spell/previous_spell via current bound native keys; observe book selection after sync":"quick_cast uses an actual configured quick-cast key: it casts, not selection-only. Selection-only is unsupported; use current native wheel/UI then read player selection.",
            "execution_tested_in_game",false);
    }
}
