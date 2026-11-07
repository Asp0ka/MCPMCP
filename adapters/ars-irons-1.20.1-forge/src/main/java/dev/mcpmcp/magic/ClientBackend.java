package dev.mcpmcp.magic;

import com.google.gson.*;
import dev.mcpmcp.api.PluginContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.*;
import static dev.mcpmcp.core.Jsons.*;

/** Reflection isolates optional mods; only verified fixed public symbols are used. No mutations of mod data. */
final class ClientBackend implements MagicProvider.Backend {
    private static final String ARS="com.hollingsworth.arsnouveau.", IRON="io.redspace.ironsspellbooks.";
    private final Pages pages=new Pages();
    private List<Recipe<?>> recipeSnapshot=List.of();
    private Object recipeIdentity=new Object();
    public JsonObject read(String mod,JsonObject a) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null)return obj("status","unknown","reason","not_in_world","source","client","complete",false);
        try {
            String view=str(a,"view","describe");
            JsonObject r=switch(view){case "player"->player(mod);case "spells"->spells(mod,a);case "recipes"->recipes(mod,a);case "machine"->machine(mod,a);default->error("unsupported_view");};
            r.addProperty("source","client_visible_snapshot");r.addProperty("observed_at_ms",System.currentTimeMillis());return r;
        }catch(ReflectiveOperationException|LinkageError e){return obj("error","target_api_unavailable","reason",e.getClass().getSimpleName(),"complete",false);}
        catch(RuntimeException e){return obj("error","client_observation_failed","reason",e.getClass().getSimpleName(),"complete",false);}
    }
    private static Object stat(String c,String method,Object...args)throws ReflectiveOperationException{return Reflect.call(Reflect.type(c),method,args);}
    private static String itemId(ItemStack s){return BuiltInRegistries.ITEM.getKey(s.getItem()).toString();}
    private static JsonObject stack(ItemStack s){return obj("item_id",itemId(s),"count",s.getCount(),"nbt",s.hasTag()?s.getTag().toString():null);}
    private static JsonObject ingredient(Ingredient i){JsonArray options=new JsonArray();ItemStack[] stacks=i.getItems();for(int n=0;n<Math.min(32,stacks.length);n++)options.add(stack(stacks[n]));return obj("alternatives",options,"total_alternatives",stacks.length,"complete",stacks.length<=32);}
    private static JsonObject unknown(String why){return obj("status","unknown","reason",why);}
    private JsonObject player(String mod)throws ReflectiveOperationException {
        Minecraft mc=Minecraft.getInstance();JsonObject r=obj("player_uuid",mc.player.getUUID().toString(),"scope","local_player_only","complete",true);
        JsonArray books=new JsonArray();
        for(int i=0;i<mc.player.getInventory().getContainerSize();i++) {
            ItemStack s=mc.player.getInventory().getItem(i);JsonObject b=book(mod,s);if(b!=null){b.addProperty("inventory_index",i);b.addProperty("slot_namespace","player_inventory_not_menu");books.add(b);}
        }
        r.add("spellbooks",books);
        if(mod.equals("ars_nouveau")) {
            Object cap=stat(ARS+"setup.registry.CapabilityRegistry","getMana",mc.player);
            Object value=Reflect.call(cap,"orElse",(Object)null);
            r.add("mana",value==null?unknown("client_capability_absent"):obj("current",Reflect.number(value,"getCurrentMana"),"maximum",Reflect.integer(value,"getMaxMana"),"authority","PacketUpdateMana synchronized client capability","sync_freshness","unknown"));
            r.addProperty("reserved_overlay_mana",((Number)Reflect.field(Reflect.type(ARS+"client.ClientInfo"),"reservedOverlayMana")).doubleValue());
            r.add("selection",arsSelection());
        } else {
            Class<?> data=Reflect.type(IRON+"player.ClientMagicData");
            Attribute max=BuiltInRegistries.ATTRIBUTE.get(net.minecraft.resources.ResourceLocation.tryParse("irons_spellbooks:max_mana"));
            r.add("mana",obj("current",Reflect.integer(data,"getPlayerMana"),"maximum",max!=null&&mc.player.getAttribute(max)!=null?mc.player.getAttributeValue(max):unknown("attribute_not_synced"),"shared_system","irons_spellbooks","sync_freshness","unknown"));
            r.add("casting",obj("active",Reflect.bool(data,"isCasting"),"spell_id",Reflect.call(data,"getCastingSpellId"),"level",Reflect.integer(data,"getCastingSpellLevel"),"remaining_ticks",Reflect.integer(data,"getCastDurationRemaining"),"duration_ticks",Reflect.integer(data,"getCastDuration")));
            Object manager=Reflect.call(data,"getSpellSelectionManager");JsonArray selections=new JsonArray();
            if(manager!=null){for(Object o:Reflect.list(Reflect.call(manager,"getAllSpells")))selections.add(selection(o));r.add("selection",obj("selection_index",Reflect.integer(manager,"getSelectionIndex"),"selected",selection(Reflect.call(manager,"getSelection")),"options",selections,"includes_curios",true));}
            else r.add("selection",unknown("selection_manager_absent"));
            JsonArray cds=new JsonArray();Object cd=Reflect.call(data,"getCooldowns");
            if(cd!=null)for(var e:((Map<?,?>)Reflect.call(cd,"getSpellCooldowns")).entrySet())if(cds.size()<128)cds.add(obj("spell_id",e.getKey().toString(),"remaining_ticks",Reflect.integer(e.getValue(),"getCooldownRemaining"),"cooldown_ticks",Reflect.integer(e.getValue(),"getSpellCooldown")));
            r.add("cooldowns",cds);r.addProperty("cooldowns_complete",cd==null||((Map<?,?>)Reflect.call(cd,"getSpellCooldowns")).size()<=128);
        }
        JsonArray equipment=new JsonArray();for(EquipmentSlot slot:EquipmentSlot.values()){ItemStack s=mc.player.getItemBySlot(slot);if(!s.isEmpty())equipment.add(obj("equipment_slot",slot.getName(),"stack",stack(s)));}r.add("equipment",equipment);
        r.add("server_only_resources",unknown("not_requested_or_available_from_client"));return r;
    }
    private JsonObject book(String mod,ItemStack original)throws ReflectiveOperationException {
        if(original.isEmpty())return null;ItemStack s=original.copy();
        if(mod.equals("ars_nouveau")) {
            if(!Reflect.is(s.getItem(),ARS+"api.spell.ISpellCasterProvider"))return null;
            Object caster=Reflect.call(s.getItem(),"getSpellCaster",s);int slots=Math.min(32,Reflect.integer(caster,"getMaxSlots"));JsonArray spells=new JsonArray();
            for(int n=0;n<slots;n++)spells.add(obj("slot",n,"name",Reflect.call(caster,"getSpellName",n),"spell",arsSpell(Reflect.call(caster,"getSpell",n))));
            return obj("stack",stack(original),"selected_signature",Reflect.call(Reflect.call(caster,"getSpell"),"serializeRecipe").toString(),"selected_slot",Reflect.integer(caster,"getCurrentSlot"),"max_slots",Reflect.integer(caster,"getMaxSlots"),"spells",spells,"complete",Reflect.integer(caster,"getMaxSlots")<=32);
        }
        if(!(Boolean)stat(IRON+"api.spells.ISpellContainer","isSpellContainer",s))return null;
        Object container=stat(IRON+"api.spells.ISpellContainer","get",s);if(container==null)return obj("stack",stack(original),"status","unknown","reason","spell_container_decode_failed");JsonArray spells=new JsonArray();
        List<?> all=Reflect.list(Reflect.call(container,"getAllSpells"));for(int n=0;n<Math.min(32,all.size());n++){Object o=all.get(n);spells.add(o==null?obj("index",n,"empty",true):obj("index",Reflect.integer(o,"index"),"empty",false,"spell",ironSpell(Reflect.call(o,"spellData"))));}
        return obj("stack",stack(original),"spells",spells,"must_equip",Reflect.bool(container,"mustEquip"),"spell_wheel",Reflect.bool(container,"isSpellWheel"),"max_spell_count",Reflect.integer(container,"getMaxSpellCount"),"complete",all.size()<=32);
    }
    private JsonObject arsSpell(Object spell)throws ReflectiveOperationException {
        List<?> recipe=Reflect.list(Reflect.call(spell,"serializeRecipe"));JsonArray ids=new JsonArray();for(Object id:recipe.subList(0,Math.min(64,recipe.size())))ids.add(id.toString());
        return obj("glyph_ids",ids,"base_mana_cost",Reflect.integer(spell,"getCost"),"valid",Reflect.bool(spell,"isValid"),"empty",Reflect.bool(spell,"isEmpty"),"complete",recipe.size()<=64,"cost_semantics","base_cost_before_player_discounts");
    }
    private JsonObject ironSpell(Object data)throws ReflectiveOperationException {
        if(data==null)return unknown("no_spell_data");if(data==Reflect.field(Reflect.type(IRON+"api.spells.SpellData"),"EMPTY"))return obj("empty",true);Object spell=Reflect.call(data,"getSpell");int level=Reflect.integer(data,"getLevel");
        return obj("spell_id",Reflect.call(spell,"getSpellId"),"level",level,"locked",Reflect.bool(data,"isLocked"),"base_mana_cost",Reflect.call(spell,"getManaCost",level),"cast_type",Reflect.call(spell,"getCastType").toString());
    }
    private JsonObject selection(Object o)throws ReflectiveOperationException {
        if(o==null)return unknown("no_selection");return obj("source",Reflect.field(o,"slot"),"source_index",Reflect.field(o,"slotIndex"),"global_index",Reflect.field(o,"globalIndex"),"spell",ironSpell(Reflect.field(o,"spellData")));
    }
    private JsonObject arsSelection()throws ReflectiveOperationException {
        Minecraft mc=Minecraft.getInstance();Object hand=stat(ARS+"api.util.StackUtil","getHeldCasterTool",mc.player);
        if(!(hand instanceof InteractionHand h))return unknown("no_held_caster");JsonObject b=book("ars_nouveau",mc.player.getItemInHand(h));if(b!=null)b.addProperty("hand",h==InteractionHand.MAIN_HAND?"main":"off");return b==null?unknown("no_held_caster"):b;
    }
    private JsonObject spells(String mod,JsonObject args)throws ReflectiveOperationException {
        Minecraft mc=Minecraft.getInstance();Object owner;List<?> all;
        if(mod.equals("ars_nouveau")){Map<?,?> map=(Map<?,?>)stat(ARS+"api.registry.GlyphRegistry","getSpellpartMap");owner=map;all=new ArrayList<>(map.values());}
        else {Object registry=Reflect.field(Reflect.type(IRON+"api.registry.SpellRegistry"),"REGISTRY");if(registry instanceof java.util.function.Supplier<?> supplier)registry=supplier.get();owner=registry;all=Reflect.list(Reflect.call(registry,"getValues"));}
        List<Object> sorted=new ArrayList<>(all);sorted.sort(Comparator.comparing(o->spellIdentifier(mod,o)));
        String query=str(args,"query","").toLowerCase(Locale.ROOT);
        return pages.take(mc.level,owner,args,sorted,o->spellIdentifier(mod,o).startsWith(mod+":")&&spellIdentifier(mod,o).toLowerCase(Locale.ROOT).contains(query),o->{try {
            if(mod.equals("ars_nouveau"))return obj("id",spellIdentifier(mod,o),"base_mana_cost",Reflect.integer(o,"getCastingCost"),"enabled",Reflect.bool(o,"isEnabled"),"tier",Reflect.call(o,"getConfigTier").toString());
            return obj("id",spellIdentifier(mod,o),"enabled",Reflect.bool(o,"isEnabled"),"max_level",Reflect.integer(o,"getMaxLevel"),"cast_type",Reflect.call(o,"getCastType").toString(),"base_cooldown",Reflect.integer(o,"getSpellCooldown"),"base_mana_cost_level_1",Reflect.call(o,"getManaCost",1));
        }catch(ReflectiveOperationException e){return obj("id",spellIdentifier(mod,o),"details",unknown("target_api_unavailable"));}});
    }
    private static String spellIdentifier(String mod,Object o){try{return Reflect.call(o,mod.equals("ars_nouveau")?"getRegistryName":"getSpellId").toString();}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}

    private JsonObject recipes(String mod,JsonObject args) {
        Minecraft mc=Minecraft.getInstance();RecipeManager manager=mc.level.getRecipeManager();
        Collection<Recipe<?>> all=manager.getRecipes();List<Recipe<?>> sorted=new ArrayList<>(all);sorted.sort(Comparator.comparing(r->r.getId().toString()));
        String query=str(args,"query","").toLowerCase(Locale.ROOT),type=str(args,"type","");
        boolean changed=sorted.size()!=recipeSnapshot.size();
        if(!changed)for(int i=0;i<sorted.size();i++)if(sorted.get(i)!=recipeSnapshot.get(i)){changed=true;break;}
        if(changed){recipeSnapshot=List.copyOf(sorted);recipeIdentity=new Object();}
        JsonObject result=pages.take(mc.level,recipeIdentity,args,sorted,o->{Recipe<?> r=(Recipe<?>)o;String id=r.getId().toString(),t=BuiltInRegistries.RECIPE_TYPE.getKey(r.getType()).toString();return (id.startsWith(mod+":")||t.startsWith(mod+":"))&&id.toLowerCase(Locale.ROOT).contains(query)&&(type.isEmpty()||type.equals(t));},o->RecipeDetails.encode((Recipe<?>)o));
        result.addProperty("scope","actual_client_RecipeManager_only");result.addProperty("automatic_execution_supported",false);
        if(!mod.equals("ars_nouveau"))result.add("not_recipe_manager",obj("scroll_forge","dynamic spell/material rules; unsupported automation","inscription_table","dynamic spellbook rules; unsupported automation","arcane_anvil","dynamic upgrades/repair/imbue rules; unsupported automation"));
        result.addProperty("runtime_recipe_changes","Includes synchronized datapack recipes, not the bundled JSON inventory.");return result;
    }
    private JsonObject machine(String mod,JsonObject a)throws ReflectiveOperationException {
        if(!a.has("x")||!a.has("y")||!a.has("z"))return error("machine_requires_x_y_z");
        Minecraft mc=Minecraft.getInstance();BlockPos pos=new BlockPos(integer(a,"x",0),integer(a,"y",0),integer(a,"z",0));
        if(!mc.level.hasChunkAt(pos))return obj("position",obj("x",pos.getX(),"y",pos.getY(),"z",pos.getZ()),"status","unknown","reason","chunk_not_loaded","complete",false);
        var state=mc.level.getBlockState(pos);BlockEntity be=mc.level.getBlockEntity(pos);String id=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        JsonObject r=obj("block_id",id,"position",obj("x",pos.getX(),"y",pos.getY(),"z",pos.getZ()),"block_state",state.toString(),"block_entity_class",be==null?null:be.getClass().getName(),"complete",true,"server_authority","unknown","sync_freshness","unknown");
        if(!id.startsWith(mod+":")){r.addProperty("error","target_mod_block_required");return r;}
        if(be==null){r.add("resources",unknown("no_client_block_entity"));return r;}
        if(mod.equals("ars_nouveau")) {
            if(Reflect.is(be,ARS+"api.source.AbstractSourceMachine"))r.add("source",obj("current",Reflect.integer(be,"getSource"),"maximum",Reflect.integer(be,"getMaxSource"),"transfer_rate",Reflect.integer(be,"getTransferRate"),"authority","ModdedTile update tag/AbstractSourceMachine source fields"));
            else r.add("source",unknown("not_a_synced_source_storage; apparatus_consumes_nearby_sources"));
            if(be instanceof Container c){JsonArray items=new JsonArray();for(int i=0;i<Math.min(32,c.getContainerSize());i++)items.add(obj("index",i,"stack",stack(c.getItem(i))));r.add("items",items);}
            if(Reflect.is(be,ARS+"common.block.tile.EnchantingApparatusTile"))r.addProperty("is_crafting",(Boolean)Reflect.field(be,"isCrafting"));
            if(Reflect.is(be,ARS+"common.block.tile.ImbuementTile"))r.addProperty("draining",(Boolean)Reflect.field(be,"draining"));
            r.add("internal_recipe_progress",unknown("server_only_or_not_exposed_by_verified_public_getters"));
        } else if(Reflect.is(be,IRON+"block.alchemist_cauldron.AlchemistCauldronTile")) {
            r.addProperty("fluid_amount_mb",Reflect.integer(be,"getFluidAmount"));JsonArray items=new JsonArray();for(Object s:Reflect.list(Reflect.field(be,"inputItems")))items.add(stack((ItemStack)s));r.add("inputs",items);
            r.add("fluids",RecipeDetails.fluidsFromHandler(Reflect.field(be,"fluidInventory")));
            r.add("cook_times",unknown("private_server_progress_not_exposed"));
        } else if(Reflect.is(be,IRON+"block.scroll_forge.ScrollForgeTile")) {
            Object handler=Reflect.call(be,"getItemHandler");JsonArray items=new JsonArray();for(int n=0;n<Math.min(32,Reflect.integer(handler,"getSlots"));n++)items.add(obj("index",n,"stack",stack((ItemStack)Reflect.call(handler,"getStackInSlot",n))));r.add("items",items);r.add("recipe",unknown("dynamic_menu_rule; read_live_menu"));
        } else r.add("resources",unknown("no_verified_client_resource_api_for_this_block"));
        return r;
    }
    public JsonObject act(String mod,String action,JsonObject a,PluginContext context)throws Exception {
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.gameMode==null)return error("not_in_world");
        if(mc.screen!=null||mc.player.containerMenu!=mc.player.inventoryMenu)return error("close_existing_screen_first");
        if(!mc.player.containerMenu.getCarried().isEmpty())return error("cursor_not_empty");
        InteractionHand hand=str(a,"hand","main").equals("off")?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND;
        if(action.equals("interact")) {
            ItemStack held=mc.player.getItemInHand(hand);if(!itemId(held).equals(str(a,"expected_item","")))return error("held_item_changed");
            BlockPos p=new BlockPos(integer(a,"x",0),integer(a,"y",0),integer(a,"z",0));if(!mc.level.hasChunkAt(p))return error("chunk_not_loaded");
            String block=BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(p).getBlock()).toString();if(!block.startsWith(mod+":")||!block.equals(str(a,"expected_block","")))return error("target_block_changed");
            return delegate(context,"block.interact",obj("x",p.getX(),"y",p.getY(),"z",p.getZ(),"expected_block",block,"face",str(a,"face","up"),"hand",str(a,"hand","main")),"read_machine_and_inventory_after_sync");
        }
        if(action.equals("use")) {
            ItemStack held=mc.player.getItemInHand(hand);if(!itemId(held).equals(str(a,"expected_item","")))return error("held_item_changed");
            JsonObject b=book(mod,held);if(b==null)return error("held_spell_container_required");
            if(mod.equals("ars_nouveau")) {
                if(!Reflect.is(held.getItem(),ARS+"common.items.SpellBook"))return error("verified_spellbook_use_required");
                Object caster=Reflect.call(held.getItem(),"getSpellCaster",held.copy());String signature=Reflect.call(Reflect.call(caster,"getSpell"),"serializeRecipe").toString();
                if(!signature.equals(str(a,"expected_spell","")))return error("selected_spell_changed");
            } else {
                // Iron spellbooks cast via their cast key; generic right-use may open other functions.
                return obj("error","unsupported","reason","Use cast or quick_cast; spell-container presence does not prove right-click casting.");
            }
            return delegate(context,"player.use",obj("hand",str(a,"hand","main"),"expected_item",str(a,"expected_item","")),"observe_mana_book_and_world_effect; submission_is_not_cast_success");
        }
        if(mod.equals("ars_nouveau")) {
            Object h=stat(ARS+"api.util.StackUtil","getHeldCasterTool",mc.player);if(!(h instanceof InteractionHand actual))return error("held_spellbook_required");
            ItemStack held=mc.player.getItemInHand(actual);if(!itemId(held).equals(str(a,"expected_item","")))return error("held_item_changed");
            if(!Reflect.is(held.getItem(),ARS+"common.items.SpellBook"))return error("verified_spellbook_required");
            String key=switch(action){case "next_spell"->"NEXT_SLOT";case "previous_spell"->"PREVIOUS_SLOT";case "open_book"->"OPEN_BOOK";default->throw new IllegalArgumentException("unsupported");};
            return key(context,(KeyMapping)Reflect.field(Reflect.type(ARS+"client.registry.ModKeyBindings"),key));
        }
        Object manager=stat(IRON+"player.ClientMagicData","getSpellSelectionManager");if(manager==null)return error("no_selection_manager");
        Object option=action.equals("quick_cast")?Reflect.call(manager,"getSpellSlot",integer(a,"index",0)):Reflect.call(manager,"getSelection");if(option==null)return error("no_selected_spell");
        JsonObject selected=selection(option);if(!selected.getAsJsonObject("spell").has("spell_id"))return error("empty_selected_spell");String spell=selected.getAsJsonObject("spell").get("spell_id").getAsString();
        if(!spell.startsWith(mod+":")||!spell.equals(str(a,"expected_spell",""))||!selected.get("source").getAsString().equals(str(a,"expected_source",""))||selected.get("source_index").getAsInt()!=integer(a,"expected_source_index",-1))return error("selected_spell_or_source_changed");
        Object mapping;
        if(action.equals("quick_cast")) {List<?> keys=Reflect.list(Reflect.field(Reflect.type(IRON+"player.KeyMappings"),"QUICK_CAST_MAPPINGS"));int index=integer(a,"index",0);if(index>=keys.size())return error("quick_cast_key_not_available");mapping=keys.get(index);}
        else if(action.equals("cast"))mapping=Reflect.field(Reflect.type(IRON+"player.KeyMappings"),"SPELLBOOK_CAST_ACTIVE_KEYMAP");else return error("unsupported");
        return key(context,(KeyMapping)mapping);
    }
    private JsonObject key(PluginContext ctx,KeyMapping mapping) {
        Minecraft mc=Minecraft.getInstance();InputConstants.Key key=mapping.getKey();int code=key.getValue();
        if(key.getType()!=InputConstants.Type.KEYSYM||code<32||code>348)return obj("error","unsupported_key_binding","reason","Bind a GLFW keyboard key locally; mouse/scancode/unbound mappings are not synthesized.");
        if(mapping.getKeyModifier()!=net.minecraftforge.client.settings.KeyModifier.NONE)return error("modified_key_binding_unsupported");
        for(KeyMapping other:mc.options.keyMappings)if(other!=mapping&&other.getKey().equals(key))return error("conflicting_key_binding");
        if(mapping.isDown()||InputConstants.isKeyDown(mc.getWindow().getWindow(),code))return error("key_already_held");
        return delegate(ctx,"ui.key",obj("key",code,"action","tap","modifiers",0),"read_selection_mana_cast_state_after_client_tick_and_server_sync");
    }
    private static JsonObject delegate(PluginContext context,String name,JsonObject args,String verify) {
        JsonObject child=context.execute(name,args);if(child.has("error"))return child;return obj("status","submitted","core_action",name,"core_job",child,"verification",verify,"observed_success",false,"guard_timing","adapter identity checks precede queued core input; core player.use rechecks expected_item; do not change equipment/menu until child finishes");
    }
}
