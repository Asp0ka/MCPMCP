package dev.mcpmcp.forge;

import com.google.gson.*;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import static dev.mcpmcp.core.Jsons.*;

/** Client-thread observation entry point. Every scanning operation counts candidates, including misses. */
public final class Observations {
    public static JsonObject read(String kind,JsonObject args) {
        if(args==null)args=new JsonObject();
        try {
            Minecraft mc=Minecraft.getInstance();
            return switch(kind) {
                case "screen"->UiActions.read(args);
                case "screenshot"->UiActions.execute("screenshot",args);
                case "recipes"->RecipeService.read(args);
                case "chat"->ChatLog.read(args);
                case "mods"->mods(args);
                case "paths"->paths();
                case "player","inventory","item","equipment","state","blocks","entities","chunks","structures"->
                    mc.level==null||mc.player==null?obj("error","world_unavailable","reason","No client world/player is loaded"):
                    world(kind,args,mc);
                default->obj("error","unknown_observation","reason",kind);
            };
        } catch(IllegalArgumentException ex) {return obj("error","invalid_arguments","reason",ex.getMessage());}
    }
    private static JsonObject world(String kind,JsonObject args,Minecraft mc) {
        if((kind.equals("chunks")||kind.equals("structures"))&&(bool(args,"server",false)||args.has("ticket")))
            return ObservServer.read(kind,args);
        return switch(kind) {
            case "player"->player(mc,args);
            case "equipment"->equipment(mc,args);
            case "inventory","item"->inventory(mc,args,kind.equals("item"));
            case "state"->state(mc);
            case "blocks"->blocks(mc,args);
            case "entities"->entities(mc,args);
            case "chunks"->chunks(mc,args);
            case "structures"->ObservJson.envelope(obj("status","unknown","reason","Structure starts/references are not synchronized to clients", 
                "singleplayer_enhancement",mc.hasSingleplayerServer()?"request server=true; poll returned ticket":"unavailable_on_remote_server"),"client","structure_metadata_not_synchronized",false,false);
            default->throw new IllegalArgumentException("unknown_kind");
        };
    }
    static boolean detailed(JsonObject args){String detail=str(args,"detail","coarse");if(!detail.equals("coarse")&&!detail.equals("detailed"))throw new IllegalArgumentException("detail_must_be_coarse_or_detailed");return detail.equals("detailed");}
    static int limit(JsonObject args,int fallback){return ObservBounds.pageSize(integer(args,"limit",fallback),Runtime.config().scanBudget);}
    public static long offset(JsonObject args,String key,long fallback) {
        double v=number(args,key,fallback);
        if(v<0||v!=Math.rint(v)||v>9007199254740991d)throw new IllegalArgumentException("invalid_"+key);return (long)v;
    }
    private static JsonObject player(Minecraft mc,JsonObject args) {
        Player p=mc.player;JsonObject d=ObservValues.entity(p,detailed(args));
        d.addProperty("dimension",mc.level.dimension().location().toString());d.addProperty("health",p.getHealth());d.addProperty("max_health",p.getMaxHealth());
        d.addProperty("absorption",p.getAbsorptionAmount());d.addProperty("food",p.getFoodData().getFoodLevel());d.addProperty("saturation",p.getFoodData().getSaturationLevel());
        d.addProperty("armor",p.getArmorValue());d.addProperty("air",p.getAirSupply());d.addProperty("max_air",p.getMaxAirSupply());
        d.addProperty("experience_level",p.experienceLevel);d.addProperty("experience_progress",p.experienceProgress);d.addProperty("experience_total",p.totalExperience);
        d.addProperty("yaw",p.getYRot());d.addProperty("pitch",p.getXRot());d.addProperty("sneaking",p.isShiftKeyDown());d.addProperty("sprinting",p.isSprinting());
        d.addProperty("selected_hotbar",p.getInventory().selected);d.addProperty("gamemode",mc.gameMode==null?"unknown":mc.gameMode.getPlayerMode().getName());
        d.add("abilities",obj("flying",p.getAbilities().flying,"may_fly",p.getAbilities().mayfly,"invulnerable",p.getAbilities().invulnerable,"instant_build",p.getAbilities().instabuild));
        JsonArray effects=new JsonArray();int n=0;for(var effect:p.getActiveEffects()) {if(n++>=128)break;
            effects.add(obj("id",BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect()).toString(),"amplifier",effect.getAmplifier(),"duration_ticks",effect.getDuration(),"ambient",effect.isAmbient(),"visible",effect.isVisible()));}
        d.add("effects",effects);d.addProperty("effects_truncated",p.getActiveEffects().size()>128);
        return ObservJson.envelope(d,"client_synchronized_player","local_player",p.getActiveEffects().size()<=128,p.getActiveEffects().size()>128);
    }
    private static JsonObject equipment(Minecraft mc,JsonObject args) {
        JsonObject d=new JsonObject();for(EquipmentSlot slot:EquipmentSlot.values())d.add(slot.getName(),ObservValues.item(mc.player.getItemBySlot(slot),detailed(args)));
        return ObservJson.envelope(d,"client_synchronized_inventory","vanilla_equipment_slots",true,false);
    }
    private static JsonObject inventory(Minecraft mc,JsonObject args,boolean one) {
        boolean detail=detailed(args);var inv=mc.player.getInventory();int size=inv.getContainerSize();
        if(one) {int slot=integer(args,"slot",inv.selected);if(slot<0||slot>=size)throw new IllegalArgumentException("invalid_inventory_slot");
            return ObservJson.envelope(obj("slot",slot,"item",ObservValues.item(inv.getItem(slot),detail)),"client_synchronized_inventory","player_inventory_slot",true,false);}
        long off=offset(args,"offset",0);if(off>size)throw new IllegalArgumentException("invalid_offset");int end=(int)Math.min(size,off+limit(args,41));
        JsonArray entries=new JsonArray();for(int i=(int)off;i<end;i++)entries.add(obj("slot",i,"item",ObservValues.item(inv.getItem(i),detail)));
        JsonObject d=obj("slots",entries,"total_slots",size,"next_offset",end<size?end:null,"selected_hotbar",inv.selected,
            "carried",ObservValues.item(mc.player.containerMenu.getCarried(),detail),"mod_extra_slots","unknown_use_plugin_observer");
        return ObservJson.envelope(d,"client_synchronized_inventory","vanilla_player_inventory",end==size,end<size);
    }
    private static JsonObject state(Minecraft mc) {
        var p=mc.player;var level=mc.level;BlockPos at=p.blockPosition(),below=at.below();
        JsonObject d=obj("position",ObservValues.vec(p.position()),"dimension",level.dimension().location().toString(),
            "game_time",level.getGameTime(),"day_time",level.getDayTime(),"raining",level.isRaining(),"thundering",level.isThundering(),
            "rain_level",level.getRainLevel(1),"thunder_level",level.getThunderLevel(1),"in_water",p.isInWater(),"in_lava",p.isInLava(),"on_ground",p.onGround(),
            "on_fire",p.isOnFire(),"paused",mc.isPaused(),"screen",mc.screen==null?null:mc.screen.getClass().getName());
        if(loaded(level,at)!=null) {d.addProperty("block_light",level.getBrightness(LightLayer.BLOCK,at));d.addProperty("sky_light",level.getBrightness(LightLayer.SKY,at));
            d.addProperty("biome",level.getBiome(at).unwrapKey().map(k->k.location().toString()).orElse("unknown"));
        }else d.addProperty("light_and_biome","unknown_unloaded_chunk");
        LevelChunk chunk=loaded(level,below);d.add("block_below",chunk==null?obj("status","unknown_unloaded_chunk"):block(chunk,below,false));
        return ObservJson.envelope(d,"client_synchronized_world","local_environment",chunk!=null,false);
    }
    static LevelChunk loaded(ClientLevel level,BlockPos p) {
        if(p.getY()<level.getMinBuildHeight()||p.getY()>=level.getMaxBuildHeight())return null;
        return level.getChunkSource().getChunk(p.getX()>>4,p.getZ()>>4,ChunkStatus.FULL,false);
    }
    static int[] bounds(JsonObject args,BlockPos center) {
        if(args.has("min")||args.has("max")) {
            if(!args.has("min")||!args.has("max"))throw new IllegalArgumentException("min_and_max_required");
            JsonObject min=object(args,"min"),max=object(args,"max");int[] b={integer(min,"x",center.getX()),integer(min,"y",center.getY()),integer(min,"z",center.getZ()),integer(max,"x",center.getX()),integer(max,"y",center.getY()),integer(max,"z",center.getZ())};
            ObservBounds.volume(b[0],b[1],b[2],b[3],b[4],b[5]);return b;
        }
        int radius=integer(args,"radius",8);if(radius<0||radius>512)throw new IllegalArgumentException("radius_out_of_range_0_512");
        int x=integer(args,"x",center.getX()),y=integer(args,"y",center.getY()),z=integer(args,"z",center.getZ());
        int[] b={Math.subtractExact(x,radius),Math.subtractExact(y,radius),Math.subtractExact(z,radius),Math.addExact(x,radius),Math.addExact(y,radius),Math.addExact(z,radius)};
        ObservBounds.volume(b[0],b[1],b[2],b[3],b[4],b[5]);return b;
    }
    private static JsonObject blocks(Minecraft mc,JsonObject args) {
        int[] b=bounds(args,mc.player.blockPosition());long size=ObservBounds.volume(b[0],b[1],b[2],b[3],b[4],b[5]);
        long off=offset(args,"offset",0);if(off>size)throw new IllegalArgumentException("invalid_offset");long end=Math.min(size,off+limit(args,256));
        JsonArray entries=new JsonArray();JsonObject counts=new JsonObject();int unloaded=0,air=0;boolean detail=detailed(args);String filter=str(args,"block","");
        for(long i=off;i<end;i++) {
            int[] xyz=ObservBounds.position(b[0],b[1],b[2],b[3],b[4],b[5],i);BlockPos pos=new BlockPos(xyz[0],xyz[1],xyz[2]);LevelChunk chunk=loaded(mc.level,pos);
            if(chunk==null){unloaded++;continue;}BlockState s=chunk.getBlockState(pos);String id=BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
            if(s.isAir()){air++;if(!bool(args,"include_air",false))continue;}if(!filter.isEmpty()&&!filter.equals(id))continue;
            entries.add(block(chunk,pos,detail));counts.addProperty(id,counts.has(id)?counts.get(id).getAsInt()+1:1);
        }
        JsonObject d=obj("blocks",entries,"counts",counts,"bounds",obj("min",new int[]{b[0],b[1],b[2]},"max",new int[]{b[3],b[4],b[5]}),
            "candidate_total",size,"examined",end-off,"offset",off,"next_offset",end<size?end:null,"unloaded_or_outside_height",unloaded,"air_examined",air,
            "ordering","x_then_y_then_z","consistency","live_pages_not_snapshot","full_server_nbt","unknown");
        return ObservJson.envelope(d,"client_loaded_chunks","requested_box_page",end==size&&unloaded==0,end<size);
    }
    static JsonObject block(LevelChunk chunk,BlockPos pos,boolean detailed) {
        BlockState s=chunk.getBlockState(pos);JsonObject o=obj("position",ObservValues.pos(pos),"id",BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString());
        if(detailed) {
            JsonObject props=new JsonObject();for(var e:s.getValues().entrySet())props.addProperty(e.getKey().getName(),String.valueOf(e.getValue()));o.add("properties",props);
            o.addProperty("air",s.isAir());o.addProperty("fluid",BuiltInRegistries.FLUID.getKey(s.getFluidState().getType()).toString());
            BlockEntity be=chunk.getBlockEntities().get(pos);
            if(be!=null){o.addProperty("block_entity_type",BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).toString());
                // Serialize client instance only on explicit request; unknown server inventory is never inferred.
                try {CompoundTag tag=be.saveWithoutMetadata();o.add("block_entity_nbt",ObservValues.nbt(tag,128,256));}
                catch(RuntimeException ex){o.addProperty("block_entity_nbt","unknown_serialization_failed");}
                o.addProperty("nbt_scope","partial_client_instance_not_full_server");}
        }return o;
    }
    private static JsonObject entities(Minecraft mc,JsonObject args) {
        int[] b=bounds(args,mc.player.blockPosition());boolean detail=detailed(args);String type=str(args,"type","");
        ObservPages.Page<Entity> page=ObservPages.take("entities",mc.level,args,limit(args,128),()->mc.level.entitiesForRendering().iterator());
        JsonArray entries=new JsonArray();JsonObject counts=new JsonObject();
        for(Entity e:page.candidates()) {
            if(e.getX()<b[0]||e.getX()>=b[3]+1d||e.getY()<b[1]||e.getY()>=b[4]+1d||e.getZ()<b[2]||e.getZ()>=b[5]+1d)continue;
            String id=BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();if(!type.isEmpty()&&!type.equals(id))continue;
            entries.add(ObservValues.entity(e,detail));counts.addProperty(id,counts.has(id)?counts.get(id).getAsInt()+1:1);
        }
        return ObservJson.envelope(obj("entities",entries,"counts",counts,"next_cursor",page.next(),"examined_total",page.examined(),"examined_this_page",page.candidates().size(),
            "consistency","live_iterator_restarts_if_entity_set_changes","full_server_nbt","unknown","bounds",b),"client_tracked_entities","requested_box_page",!page.more(),page.more());
    }
    static int[] chunkBounds(JsonObject args,BlockPos player) {
        int radius=integer(args,"radius",2);if(radius<0||radius>64)throw new IllegalArgumentException("chunk_radius_out_of_range_0_64");
        int x=integer(args,"chunk_x",player.getX()>>4),z=integer(args,"chunk_z",player.getZ()>>4);
        if(Math.abs((long)x)>1875000||Math.abs((long)z)>1875000)throw new IllegalArgumentException("chunk_outside_world");
        return new int[]{x-radius,z-radius,x+radius,z+radius};
    }
    private static JsonObject chunks(Minecraft mc,JsonObject args) {
        int[] b=chunkBounds(args,mc.player.blockPosition());long width=(long)b[2]-b[0]+1,total=width*((long)b[3]-b[1]+1),off=offset(args,"offset",0);
        if(off>total)throw new IllegalArgumentException("invalid_offset");long end=Math.min(total,off+limit(args,128));JsonArray list=new JsonArray();int misses=0;
        for(long i=off;i<end;i++){int x=(int)(b[0]+i%width),z=(int)(b[1]+i/width);LevelChunk c=mc.level.getChunkSource().getChunk(x,z,ChunkStatus.FULL,false);
            if(c==null){misses++;list.add(obj("x",x,"z",z,"loaded",false));}else list.add(obj("x",x,"z",z,"loaded",true,"empty",c.isEmpty(),"status",c.getStatus().toString(),"block_entities",c.getBlockEntities().size(),"slime_chunk","unknown_seed_not_synchronized","structures","unknown_not_synchronized"));}
        return ObservJson.envelope(obj("chunks",list,"offset",off,"next_offset",end<total?end:null,"examined",end-off,"candidate_total",total,"unloaded",misses,"generation",false),"client_chunk_cache","chunk_rectangle_page",end==total,end<total);
    }
    private static JsonObject mods(JsonObject args) {
        var mods=ModList.get().getMods();long off=offset(args,"offset",0);if(off>mods.size())throw new IllegalArgumentException("invalid_offset");int end=(int)Math.min(mods.size(),off+limit(args,128));JsonArray list=new JsonArray();
        for(int i=(int)off;i<end;i++){var m=mods.get(i);list.add(obj("id",m.getModId(),"name",ObservJson.clipped(m.getDisplayName(),256),"version",m.getVersion().toString()));}
        return ObservJson.envelope(obj("mods",list,"total",mods.size(),"next_offset",end<mods.size()?end:null),"forge_mod_list","current_client_mods",end==mods.size(),end<mods.size());
    }
    private static JsonObject paths() {
        Path game=FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
        return ObservJson.envelope(obj("game",game.toString(),"mods",FMLPaths.MODSDIR.get().toAbsolutePath().normalize().toString(),"config",FMLPaths.CONFIGDIR.get().toAbsolutePath().normalize().toString(),
            "plugins",game.resolve("MCPMCP-Plugin").toString(),"logs",game.resolve("logs").toString(),"screenshots",game.resolve("screenshots").toString(),"saves",game.resolve("saves").toString()),"forge_paths","current_instance_paths",true,false);
    }
    private Observations(){}
}
