package dev.mcpmcp.forge;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import static dev.mcpmcp.core.Jsons.*;

/** DTO conversion reads synchronized client fields; it never claims full server NBT. */
public final class ObservValues {
    public static JsonObject pos(BlockPos p){return obj("x",p.getX(),"y",p.getY(),"z",p.getZ());}
    public static JsonObject vec(Vec3 v){return obj("x",v.x,"y",v.y,"z",v.z);}
    public static JsonObject box(AABB b){return obj("min",new double[]{b.minX,b.minY,b.minZ},"max",new double[]{b.maxX,b.maxY,b.maxZ});}
    public static JsonObject item(ItemStack stack,boolean detailed) {
        if(stack.isEmpty())return obj("empty",true,"count",0);
        JsonObject o=obj("empty",false,"id",BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),"count",stack.getCount(),
            "name",ObservJson.clipped(stack.getHoverName().getString(),256));
        if(detailed){o.addProperty("damage",stack.getDamageValue());o.addProperty("max_damage",stack.getMaxDamage());
            o.add("nbt",nbt(stack.getTag(),128,256));o.addProperty("nbt_scope","client_item_bounded");}
        return o;
    }
    public static JsonObject entity(Entity e,boolean detailed) {
        JsonObject o=obj("id",e.getId(),"uuid",e.getUUID().toString(),"type",BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString(),
            "name",ObservJson.clipped(e.getName().getString(),256),"position",vec(e.position()));
        if(detailed) {
            o.add("velocity",vec(e.getDeltaMovement()));o.add("bounds",box(e.getBoundingBox()));
            o.addProperty("yaw",e.getYRot());o.addProperty("pitch",e.getXRot());o.addProperty("pose",e.getPose().name());
            o.addProperty("on_ground",e.onGround());o.addProperty("in_water",e.isInWater());o.addProperty("in_lava",e.isInLava());
            o.addProperty("on_fire",e.isOnFire());o.addProperty("air",e.getAirSupply());
            if(e.getVehicle()!=null)o.addProperty("vehicle_id",e.getVehicle().getId());
            if(e instanceof LivingEntity l){o.addProperty("health",l.getHealth());o.addProperty("max_health",l.getMaxHealth());
                o.addProperty("armor",l.getArmorValue());JsonObject gear=new JsonObject();
                for(EquipmentSlot slot:EquipmentSlot.values())gear.add(slot.getName(),item(l.getItemBySlot(slot),false));o.add("equipment",gear);}
            o.addProperty("full_server_nbt","unknown");
        }
        return o;
    }
    /** Direct bounded traversal avoids allocating an unbounded SNBT string. */
    public static JsonObject nbt(Tag tag,int nodes,int chars) {
        int[] left={nodes};boolean[] cut={false};JsonElement value=nbtValue(tag,left,chars,0,cut);
        return obj("value",value,"truncated",cut[0],"node_limit",nodes,"depth_limit",8);
    }
    private static JsonElement nbtValue(Tag tag,int[] left,int chars,int depth,boolean[] cut) {
        if(tag==null)return JsonNull.INSTANCE;
        if(left[0]--<=0||depth>8){cut[0]=true;return new JsonPrimitive("[truncated]");}
        if(tag instanceof CompoundTag c){JsonObject o=new JsonObject();for(String key:c.getAllKeys()){
            if(left[0]<=0){cut[0]=true;break;}o.add(ObservJson.clipped(key,128),nbtValue(c.get(key),left,chars,depth+1,cut));}return o;}
        if(tag instanceof CollectionTag<?> list){JsonArray a=new JsonArray();for(int i=0;i<list.size();i++){
            if(left[0]<=0){cut[0]=true;break;}a.add(nbtValue(list.get(i),left,chars,depth+1,cut));}return a;}
        if(tag instanceof NumericTag n){double v=n.getAsDouble();return Double.isFinite(v)?new JsonPrimitive(n.getAsNumber()):new JsonPrimitive("nonfinite");}
        String s=tag.getAsString();if(s.length()>chars)cut[0]=true;return new JsonPrimitive(ObservJson.clipped(s,chars));
    }
    private ObservValues(){}
}
