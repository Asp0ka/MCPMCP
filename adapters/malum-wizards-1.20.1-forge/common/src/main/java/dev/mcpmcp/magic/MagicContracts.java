package dev.mcpmcp.magic;

import com.google.gson.*;
import java.util.*;
import static dev.mcpmcp.core.Jsons.*;

/** Discoverable closed schemas shared by the two independently packaged providers. */
public final class MagicContracts {
    public static final List<String> OBSERVERS=List.of("","recipes","recipe","block","blocks","menu");
    public static JsonObject schema(String part) {
        JsonObject p=new JsonObject(); JsonArray required=new JsonArray();
        JsonObject s=obj("type","object","properties",p,"required",required,"additionalProperties",false);
        // obj serializes a copy, so use the schema's own child objects.
        p=s.getAsJsonObject("properties"); required=s.getAsJsonArray("required");
        switch(part) {
            case "" -> s.addProperty("description","Availability, exact target, observer/action schemas, examples and client-only scope. Works outside a world.");
            case "recipes" -> {
                s.addProperty("description","Current client RecipeManager entries of this mod's recipe types/classes, including datapack IDs in other namespaces. Does not use bundled JSON or JEI. Empty incomplete pages must be continued.");
                field(p,"query","string","Case-insensitive ID, recipe type or actual class substring. Default empty.").addProperty("maxLength",256);
                field(p,"type","string","Optional exact recipe type ID, obtained from entries or the root contract.");
                paging(p);
            }
            case "recipe" -> {required.add("recipe_id"); field(p,"recipe_id","string","Exact current recipe ID. Returns special inputs, outputs and costs; no craft execution.");}
            case "block" -> { position(p,required); s.addProperty("description","One loaded block position; returns target IDs/classes, client-visible resource/item/progress fields. Unloaded, absent and unsupported are distinct. No chunk loading or server NBT."); }
            case "blocks" -> {
                position(p,required); bounds(field(p,"radius","integer","Cube radius 0..8, default 2. Required center within 64 blocks of the player; at most 4913 candidate positions."),0,8);
                field(p,"block_id","string","Optional exact block registry ID filter. Only this mod's blocks are returned.");
                paging(p); s.addProperty("description","Loaded local mod work blocks. scan_budget counts all cube candidates, including air, unloaded positions and filter misses. next_cursor resumes the same query/world for 120 seconds.");
            }
            case "menu" -> s.addProperty("description","Current supported mod menu, actual owner block entity, cursor and dynamic menu/container slot mapping. Uses live objects, never assumes fixed slot numbers. Other menus are reported as unsupported.");
            case "interact" -> {
                position(p,required);
                for(String k:List.of("dimension","expected_block","expected_item","expected_count","expected_nbt"))required.add(k);
                field(p,"dimension","string","Exact dimension registry ID from block observation; prevents acting in a different world dimension.");
                field(p,"expected_block","string","Exact observed block registry ID.");
                field(p,"expected_item","string","Exact held item ID; empty hand is minecraft:air. No inventory edits are performed.");
                bounds(field(p,"expected_count","integer","Exact held count from inventory; empty hand is 0."),0,2147483647);
                field(p,"expected_nbt","string","Exact held SNBT string; empty string means no tag. Does not parse or write NBT.").addProperty("maxLength",16384);
                field(p,"hand","string","main (default) or off.").add("enum",GSON.toJsonTree(List.of("main","off")));
                field(p,"face","string","Visible outline face. Default up. Refuses hidden or out-of-reach faces.").add("enum",GSON.toJsonTree(List.of("up","down","north","south","east","west")));
                s.addProperty("description","One native gameMode.useItemOn through normal player input. SIMULATE/MIXED only. Requires no screen/container or active item/use/attack input, empty cursor, loaded allowlisted mod block entity, normal build permission and visible reachable face. Submission does not prove menu open, material transfer, ritual start or completion.");
            }
            default -> throw new IllegalArgumentException("unknown_magic_contract");
        }
        return s;
    }
    private static void position(JsonObject p,JsonArray required) {
        for(String k:List.of("x","y","z")){ required.add(k);bounds(field(p,k,"integer","World block coordinate."),-30000000,30000000); }
    }
    private static void paging(JsonObject p) {
        bounds(field(p,"limit","integer","Returned matches per call, 1..32, default 16."),1,32);
        bounds(field(p,"scan_budget","integer","Candidate positions/recipes examined per call, 1..1024, default 256."),1,1024);
        field(p,"cursor","string","Opaque next_cursor. Keep every filter and center/radius unchanged. Expires on source/world change or after 120 seconds.");
    }
    private static JsonObject field(JsonObject p,String key,String type,String description) {
        JsonObject f=obj("type",type,"description",description);if(type.equals("string"))f.addProperty("maxLength",512);p.add(key,f);return f;
    }
    private static void bounds(JsonObject f,int min,int max){f.addProperty("minimum",min);f.addProperty("maximum",max);}
    public static JsonObject discovery(String mod) {
        JsonObject contracts=new JsonObject();
        for(String p:OBSERVERS)contracts.add(p.isEmpty()?mod:mod+"."+p,schema(p));
        contracts.add(mod+".interact",schema("interact"));
        return obj("source",mod,"adapter_version","0.1.0","contracts",contracts,
            "actions",List.of(mod+".interact"),"scope","client_loaded_only","server_state","unknown",
            "core_recipes_replaced",false,"automatic_rituals",false,
            "examples",List.of(obj("kind",mod+".blocks","arguments",obj("x",0,"y",64,"z",0,"radius",2,"limit",8,"scan_budget",128)),
                obj("kind",mod+".recipes","arguments",obj("query","","limit",16,"scan_budget",128)),
                obj("kind",mod+".menu","arguments",obj())));
    }
    private MagicContracts(){}
}
