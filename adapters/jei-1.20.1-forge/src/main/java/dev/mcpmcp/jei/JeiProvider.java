package dev.mcpmcp.jei;

import com.google.gson.*;
import dev.mcpmcp.api.*;
import dev.mcpmcp.core.Schema;
import java.util.*;

/** Optional provider: never replaces the core recipe or crafting implementations. */
public final class JeiProvider implements McpmcpPlugin {
    public interface Backend { JsonObject read(String kind, JsonObject args) throws Exception; JsonObject act(String action, JsonObject args) throws Exception; }
    private final Backend backend;
    public JeiProvider() { this(TargetBackend.create()); }
    public JeiProvider(Backend backend) { this.backend=Objects.requireNonNull(backend); }
    public String id() { return "mcpmcp.jei"; }
    public void register(PluginContext ctx) {
        for (String k : List.of("jei", "jei.categories", "jei.ingredients", "jei.recipes", "jei.recipe", "jei.transfer"))
            ctx.observer(k,100,args -> { Schema.validate(args,schema(k)); return backend.read(k,args); });
        for (String k : List.of("jei.transfer", "jei.show"))
            ctx.action(k,schema(k+".action"),"input","normal",args -> { Schema.validate(args,schema(k+".action")); return backend.act(k,args); });
    }
    public static JsonObject schema(String kind) {
        JsonObject s=new JsonObject(),p=new JsonObject(); s.addProperty("type","object");s.add("properties",p);s.addProperty("additionalProperties",false);
        JsonArray required=new JsonArray();s.add("required",required);
        switch(kind) {
            case "jei" -> s.addProperty("description","JEI adapter status, available observer kinds, schemas and examples. Read before using this optional provider.");
            case "jei.categories", "jei.ingredients", "jei.recipes" -> {
                field(p,"cursor","string","Opaque next_cursor from the preceding page; keep all other filters unchanged.");
                bounds(field(p,"limit","integer","Results per page, default 32."),1,64);
                bounds(field(p,"scan_budget","integer","Raw candidates examined this call, default 512. Empty incomplete pages must be continued."),1,2048);
                bounds(field(p,"query","string","Case-insensitive display-name, identifier or UID substring. Does not modify JEI's search box."),0,256);
                if(!kind.equals("jei.ingredients")) field(p,"include_hidden","boolean","Default false; explicitly include JEI-hidden categories/recipes.");
                if(kind.equals("jei.ingredients")) field(p,"ingredient_type","string","Optional registered JEI ingredient type UID; supports fluids and custom types.");
                if(kind.equals("jei.recipes")) {
                    required.add("category"); field(p,"category","string","Exact category ID returned by jei.categories.");
                    field(p,"item_id","string","Optional exact item registry ID focus, e.g. minecraft:oak_planks.");
                    field(p,"ingredient_type","string","Use with ingredient_uid to focus a specific JEI subtype, fluid or custom ingredient.");
                    field(p,"ingredient_uid","string","Exact UID from jei.ingredients, use with ingredient_type; mutually exclusive with item_id.");
                    JsonObject role=field(p,"role","string","Focus role, default output; input looks up usages.");role.add("enum",JsonParser.parseString("[\"input\",\"output\",\"catalyst\"]"));
                }
            }
            case "jei.recipe" -> { required.add("handle");field(p,"handle","string","Opaque recipe handle from jei.recipes; expires after two minutes or a runtime/world change."); }
            case "jei.transfer" -> { required.add("transfer_token"); field(p,"transfer_token","string","Token from jei.transfer action. Reports callback and menu-item changes; does not claim crafted output."); }
            case "jei.transfer.action" -> { required.add("handle");field(p,"handle","string","Use a fresh handle from jei.recipes. Requires an already-open compatible container, empty cursor, NORMAL and input-capable transport.");field(p,"max_transfer","boolean","Default false: one recipe set; true requests JEI maximum. This is filling, not automatic crafting.");field(p,"dry_run","boolean","Default true. false explicitly submits once after the same preflight."); }
            case "jei.show.action" -> { required.add("handle");field(p,"handle","string","Explicitly open this recipe in JEI. Refuses unrelated open screens, including local permission settings."); }
            default -> throw new IllegalArgumentException("unknown_jei_contract");
        }
        return s;
    }
    private static JsonObject field(JsonObject p,String k,String type,String d) { JsonObject f=new JsonObject();f.addProperty("type",type);f.addProperty("description",d);if(type.equals("string"))f.addProperty("maxLength",8192);p.add(k,f);return f; }
    private static void bounds(JsonObject f,int min,int max) { if(f.get("type").getAsString().equals("string")){f.addProperty("minLength",min);f.addProperty("maxLength",max);}else{f.addProperty("minimum",min);f.addProperty("maximum",max);} }
}
