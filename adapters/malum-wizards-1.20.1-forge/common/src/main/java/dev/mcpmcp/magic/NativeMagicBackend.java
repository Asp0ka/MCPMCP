package dev.mcpmcp.magic;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandler;
import java.util.*;
import static dev.mcpmcp.core.Jsons.*;

/** Read only live client objects; the sole write is one native player right-click. */
public abstract class NativeMagicBackend implements MagicProvider.Backend {
    protected final String mod,version,hash,classPrefix;
    private final PageTokens pages=new PageTokens();
    private Object world; private List<Recipe<?>> source=List.of();
    protected NativeMagicBackend(String mod,String version,String hash,String classPrefix){this.mod=mod;this.version=version;this.hash=hash;this.classPrefix=classPrefix;}
    protected abstract JsonObject specialRecipe(Recipe<?> recipe);
    protected abstract JsonObject specialBlock(BlockEntity entity);
    protected abstract boolean supported(BlockEntity entity);
    protected abstract BlockEntity menuOwner(AbstractContainerMenu menu);
    protected abstract List<String> knownRecipeTypes();
    protected abstract List<String> limitations();
    public final JsonObject read(String part,JsonObject args){
        try {
            Minecraft mc=Minecraft.getInstance();if(!mc.isSameThread())return error("client_thread_required");
            if(world!=mc.level){world=mc.level;source=List.of();}
            if(part.isEmpty())return obj("data",obj("mod_id",mod,"mod_loaded",ModList.get().isLoaded(mod),
                "actual_version",ModList.get().getModContainerById(mod).map(c->c.getModInfo().getVersion().toString()).orElse("unknown"),
                "target_version",version,"target_sha256",hash,"actual_hash","query_mcpmcp_get_plugins_for_manager_validated_hash","in_world",mc.level!=null&&mc.player!=null,
                "recipe_types",knownRecipeTypes(),"limitations",limitations(),"server_inventory","unknown","sync_age","unknown","server_acceptance","unknown"));
            if(mc.level==null||mc.player==null)return error("not_in_world");
            return switch(part){
                case "recipes"->recipes(args);
                case "recipe"->recipeDetail(args);
                case "block"->obj("data",block(position(args)));
                case "blocks"->blocks(args);
                case "menu"->obj("data",menu());
                default->error("unknown_magic_observer");
            };
        }catch(Exception|LinkageError e){return failure(e);}
    }
    private boolean belongs(Recipe<?> r){net.minecraft.resources.ResourceLocation k=BuiltInRegistries.RECIPE_TYPE.getKey(r.getType());return r.getClass().getName().startsWith(classPrefix)||(k!=null&&k.getNamespace().equals(mod));}
    private List<Recipe<?>> recipeSource(){
        List<Recipe<?>> now=new ArrayList<>();for(Recipe<?> r:Minecraft.getInstance().level.getRecipeManager().getRecipes())if(belongs(r))now.add(r);
        now.sort(Comparator.comparing(r->r.getId().toString()));
        if(!now.equals(source))source=List.copyOf(now);return source;
    }
    private JsonObject summary(Recipe<?> r){return obj("recipe_id",r.getId().toString(),"type",String.valueOf(BuiltInRegistries.RECIPE_TYPE.getKey(r.getType())),"class",r.getClass().getName());}
    private JsonObject recipes(JsonObject a){
        List<Recipe<?>> all=recipeSource();String q=str(a,"query","").toLowerCase(Locale.ROOT),type=str(a,"type","");
        String scope="recipes:"+canonical(a);int offset=pages.resolve(str(a,"cursor",""),all,scope),scanned=0;
        int limit=integer(a,"limit",16),budget=integer(a,"scan_budget",256);JsonArray out=new JsonArray();
        while(offset<all.size()&&scanned<budget&&out.size()<limit){Recipe<?> r=all.get(offset++);scanned++;JsonObject s=summary(r);
            if((type.isEmpty()||s.get("type").getAsString().equals(type))&&s.toString().toLowerCase(Locale.ROOT).contains(q))out.add(s);
        }
        boolean complete=offset>=all.size();return obj("source","client_recipe_manager","entries",out,"scanned",scanned,"candidate_count",all.size(),"complete",complete,
            "next_cursor",complete?null:pages.next(all,scope,offset),"server_only_recipes","unknown","automatic_crafting",false);
    }
    private JsonObject recipeDetail(JsonObject a){
        String id=str(a,"recipe_id","");
        for(Recipe<?> r:recipeSource())if(r.getId().toString().equals(id)){
            JsonObject data=summary(r);data.addProperty("source","client_recipe_manager");data.add("special",specialRecipe(r));data.addProperty("executable",false);
            return obj("data",data);
        }return error("recipe_not_in_current_client_manager");
    }
    private JsonObject blocks(JsonObject a){
        Minecraft mc=Minecraft.getInstance();BlockPos center=position(a);int radius=integer(a,"radius",2),side=radius*2+1,total=side*side*side;
        if(center.distSqr(mc.player.blockPosition())>4096)return error("scan_center_outside_local_64_blocks");
        String scope="blocks:"+canonical(a),filter=str(a,"block_id","");int offset=pages.resolve(str(a,"cursor",""),mc.level,scope);
        int scanned=0,unloaded=0,budget=integer(a,"scan_budget",256),limit=integer(a,"limit",16);JsonArray out=new JsonArray();
        while(offset<total&&scanned<budget&&out.size()<limit){int i=offset++;scanned++;BlockPos p=center.offset(i%side-radius,i/side%side-radius,i/(side*side)-radius);
            if(mc.level.isOutsideBuildHeight(p)||!mc.level.hasChunkAt(p)){unloaded++;continue;}
            var id=BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(p).getBlock());
            if(id.getNamespace().equals(mod)&&(filter.isEmpty()||id.toString().equals(filter)))out.add(block(p));
        }boolean complete=offset>=total;return obj("source","client_loaded_blocks","entries",out,"scanned",scanned,"unloaded_candidates",unloaded,
            "candidate_count",total,"complete",complete,"loaded_coverage_complete",false,"next_cursor",complete?null:pages.next(mc.level,scope,offset),
            "note","complete describes candidate enumeration, not knowledge of unloaded blocks; unloaded count is per page");
    }
    private JsonObject block(BlockPos p){
        Minecraft mc=Minecraft.getInstance();JsonObject d=obj("position",pos(p),"dimension",mc.level.dimension().location().toString(),"source","client_block_entity_copy",
            "server_state","unknown","sync_age","unknown","server_acceptance_confirmed",false);
        if(mc.level.isOutsideBuildHeight(p)||!mc.level.getWorldBorder().isWithinBounds(p)){d.addProperty("status","outside_world");return d;}
        if(!mc.level.hasChunkAt(p)){d.addProperty("status","unloaded");return d;}
        var state=mc.level.getBlockState(p);var id=BuiltInRegistries.BLOCK.getKey(state.getBlock());
        d.addProperty("block_id",id.toString());d.addProperty("block_class",state.getBlock().getClass().getName());d.addProperty("block_state",state.toString());
        if(!id.getNamespace().equals(mod)){d.addProperty("status","not_target_mod");return d;}
        BlockEntity be=mc.level.getBlockEntity(p);
        if(be==null){d.addProperty("status","no_block_entity");return d;}
        d.addProperty("block_entity_class",be.getClass().getName());d.addProperty("block_entity_type",Objects.toString(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()),"unknown"));
        d.addProperty("status",supported(be)?"supported":"unsupported");d.add("client_visible",specialBlock(be));
        d.addProperty("distance_to_center",mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(p)));d.addProperty("interaction_possible","unknown_until_visible_face_preflight");
        return d;
    }
    private JsonObject menu(){
        Minecraft mc=Minecraft.getInstance();AbstractContainerMenu menu=mc.player.containerMenu;BlockEntity owner=menuOwner(menu);
        JsonObject d=obj("class",menu.getClass().getName(),"menu_id",menu.containerId,"state_id",menu.getStateId(),"cursor",stack(menu.getCarried()),
            "supported",owner!=null,"server_acceptance","unknown","core_actions",List.of("container.move","container.sequence","container.close"));
        if(owner==null)return d;
        boolean current=owner.getLevel()==mc.level&&mc.level.hasChunkAt(owner.getBlockPos())&&mc.level.getBlockEntity(owner.getBlockPos())==owner;
        d.addProperty("owner_loaded_identity_match",current);d.add("position",pos(owner.getBlockPos()));d.addProperty("dimension",mc.level.dimension().location().toString());
        d.addProperty("block_entity_class",owner.getClass().getName());d.addProperty("still_valid",current&&menu.stillValid(mc.player));
        double distance=mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(owner.getBlockPos()));
        d.addProperty("distance_to_center",distance);d.addProperty("within_normal_reach",distance<=Math.min(6,mc.player.getAttributeValue(ForgeMod.BLOCK_REACH.get())));
        d.addProperty("sync_ready","unknown; core container state machine verifies subsequent item changes");
        JsonArray slots=new JsonArray();for(int i=0;i<Math.min(256,menu.slots.size());i++){Slot s=menu.slots.get(i);slots.add(obj("slot",i,"container_slot",s.getContainerSlot(),
            "slot_class",s.getClass().getName(),"inventory_class",s.container.getClass().getName(),"player_inventory",s.container==mc.player.getInventory(),"item",stack(s.getItem()),"may_pickup",s.mayPickup(mc.player),"slot_limit",s.getMaxStackSize()));}
        d.add("slots",slots);d.addProperty("complete",menu.slots.size()<=256);d.addProperty("note","slot is the live menu index; container_slot is not an action slot. Re-read after every menu change. Explicitly open first, then reuse current menu without target. No custom recipe filling or automatic rituals.");return d;
    }
    public final JsonObject interact(JsonObject a){
        try {
            Minecraft mc=Minecraft.getInstance();if(!mc.isSameThread())return error("client_thread_required");
            if(mc.player==null||mc.level==null||mc.gameMode==null||mc.getConnection()==null)return error("not_in_world");
            BlockPos p=position(a);boolean loaded=!mc.level.isOutsideBuildHeight(p)&&mc.level.hasChunkAt(p)&&mc.level.getWorldBorder().isWithinBounds(p);
            BlockEntity entity=loaded?mc.level.getBlockEntity(p):null;var state=loaded?mc.level.getBlockState(p):null;
            InteractionHand hand=str(a,"hand","main").equals("off")?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND;ItemStack held=mc.player.getItemInHand(hand);
            String reason=InteractionGuard.rejection(new InteractionGuard.State(true,mc.level.dimension().location().toString().equals(str(a,"dimension","")),
                mc.screen==null,mc.player.containerMenu==mc.player.inventoryMenu,mc.player.containerMenu.getCarried().isEmpty(),
                mc.player.isAlive()&&!mc.player.isSleeping()&&!mc.player.isSpectator(),mc.player.getAbilities().mayBuild&&mc.level.mayInteract(mc.player,p),
                loaded,entity!=null&&supported(entity),loaded&&BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(str(a,"expected_block","")),
                BuiltInRegistries.ITEM.getKey(held.getItem()).toString().equals(str(a,"expected_item","")),held.getCount()==integer(a,"expected_count",-1),
                (held.hasTag()?held.getTag().toString():"").equals(str(a,"expected_nbt",""))));
            if(reason!=null)return error(reason);
            if(!BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals(mod))return error("not_target_mod");
            Direction face=Direction.valueOf(str(a,"face","up").toUpperCase(Locale.ROOT));var shape=state.getShape(mc.level,p);
            if(shape.isEmpty())return error("block_has_no_outline_shape");
            Vec3 eye=mc.player.getEyePosition(),point=null;double reach=Math.min(6,mc.player.getAttributeValue(ForgeMod.BLOCK_REACH.get())),best=Double.POSITIVE_INFINITY;
            for(AABB box:shape.toAabbs()){double x=(box.minX+box.maxX)/2,y=(box.minY+box.maxY)/2,z=(box.minZ+box.maxZ)/2;
                switch(face){case UP->y=box.maxY;case DOWN->y=box.minY;case EAST->x=box.maxX;case WEST->x=box.minX;case SOUTH->z=box.maxZ;case NORTH->z=box.minZ;}
                Vec3 candidate=new Vec3(p.getX()+x,p.getY()+y,p.getZ()+z);double distance=eye.distanceTo(candidate);if(distance>reach||distance>=best)continue;
                BlockHitResult hit=mc.level.clip(new ClipContext(eye,candidate.subtract(Vec3.atLowerCornerOf(face.getNormal()).scale(0.001)),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,mc.player));
                if(hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(p)&&hit.getDirection()==face){point=hit.getLocation();best=distance;}
            }
            if(point==null)return error("target_face_occluded_or_out_of_reach");
            if(mc.player.isUsingItem()||mc.options.keyUse.isDown()||mc.options.keyAttack.isDown())return error("user_input_already_active");
            // No inventory mutation, ritual method, arbitrary packet or server accessor is called.
            var result=mc.gameMode.useItemOn(mc.player,hand,new BlockHitResult(point,face,p,false));
            return obj("delivery","submitted","native_result",result.name(),"source",mod,"position",pos(p),"hand",hand.name(),
                "server_acceptance_confirmed",false,"ritual_completed",false,"verification","Read this block, menu and player inventory; native client prediction is not server confirmation.");
        }catch(Exception|LinkageError e){return failure(e);}
    }
    protected static JsonObject stack(ItemStack s){return obj("item_id",BuiltInRegistries.ITEM.getKey(s.getItem()).toString(),"count",s.getCount(),"nbt",s.hasTag()?s.getTag().toString():"");}
    protected static JsonObject ingredient(Ingredient in){JsonArray items=new JsonArray();ItemStack[] all=in.getItems();for(int i=0;i<Math.min(128,all.length);i++)items.add(stack(all[i]));
        return obj("ingredient",in.toJson(),"items",items,"complete",all.length<=128,"count",1);}
    protected static JsonArray ingredients(List<Ingredient> in){JsonArray a=new JsonArray();for(Ingredient i:in)a.add(ingredient(i));return a;}
    protected static JsonObject inventory(IItemHandler h){JsonArray slots=new JsonArray();for(int i=0;i<Math.min(128,h.getSlots());i++)slots.add(obj("container_slot",i,"item",stack(h.getStackInSlot(i)),"limit",h.getSlotLimit(i)));return obj("slots",slots,"complete",h.getSlots()<=128,"source","client_block_entity_copy");}
    protected static JsonObject inventory(Container h){JsonArray slots=new JsonArray();for(int i=0;i<Math.min(128,h.getContainerSize());i++)slots.add(obj("container_slot",i,"item",stack(h.getItem(i))));return obj("slots",slots,"complete",h.getContainerSize()<=128,"source","client_block_entity_copy");}
    protected static JsonObject fluid(FluidStack s){return obj("fluid_id",BuiltInRegistries.FLUID.getKey(s.getFluid()).toString(),"amount_mb",s.getAmount(),"nbt",s.hasTag()?s.getTag().toString():"");}
    protected static JsonObject unknown(){return obj("resources","unknown","items","unknown","progress","unknown","recipe","unknown");}
    protected static void finite(JsonObject o,String k,float n){if(Float.isFinite(n))o.addProperty(k,n);else o.addProperty(k,"unknown_non_finite_client_value");}
    private static BlockPos position(JsonObject a){return new BlockPos(integer(a,"x",0),integer(a,"y",0),integer(a,"z",0));}
    private static JsonObject pos(BlockPos p){return obj("x",p.getX(),"y",p.getY(),"z",p.getZ());}
    private static String canonical(JsonObject a){TreeMap<String,JsonElement> fields=new TreeMap<>();a.entrySet().forEach(e->{if(!Set.of("cursor","limit","scan_budget").contains(e.getKey()))fields.put(e.getKey(),e.getValue());});return fields.toString();}
    private static JsonObject failure(Throwable e){return obj("error","adapter_read_or_interact_failed","reason",e.getClass().getSimpleName()+": "+Objects.toString(e.getMessage(),""));}
}
