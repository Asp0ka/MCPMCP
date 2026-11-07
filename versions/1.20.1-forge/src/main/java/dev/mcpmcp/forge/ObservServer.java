package dev.mcpmcp.forge;

import com.google.gson.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import static dev.mcpmcp.core.Jsons.*;

/** Explicit integrated-server enhancement. Submit only immutable request values across thread boundary. */
final class ObservServer {
    private record Pending(Object world,String kind,CompletableFuture<JsonObject> future,long created) {}
    private static final LinkedHashMap<String,Pending> PENDING=new LinkedHashMap<>();
    static JsonObject read(String kind,JsonObject args) {
        Minecraft mc=Minecraft.getInstance();long now=System.currentTimeMillis();
        PENDING.entrySet().removeIf(e->e.getValue().world!=mc.level||now-e.getValue().created>60000);
        String ticket=str(args,"ticket","");
        if(!ticket.isEmpty()) {
            Pending p=PENDING.get(ticket);
            if(p==null||!p.kind.equals(kind))return obj("error","stale_or_invalid_ticket","reason","Ticket expired, belongs to another world, or has a different kind");
            if(!p.future.isDone())return pending(ticket);
            PENDING.remove(ticket);
            try{return p.future.join();}catch(RuntimeException e){return obj("error","server_observation_failed","reason","Integrated server request could not complete");}
        }
        IntegratedServer server=mc.getSingleplayerServer();
        if(server==null)return ObservJson.envelope(obj("status","unknown","reason","Remote server metadata is not available locally"),"client","server_metadata",false,false);
        int[] b=Observations.chunkBounds(args,mc.player.blockPosition());int limit=Observations.limit(args,128);
        int startsOffset=integer(args,"starts_offset",0);if(startsOffset<0||startsOffset>100000)throw new IllegalArgumentException("invalid_starts_offset");long offset=Observations.offset(args,"offset",0);long total=((long)b[2]-b[0]+1)*((long)b[3]-b[1]+1);
        if(offset>total)throw new IllegalArgumentException("invalid_offset");
        ResourceKey<Level> dimension=mc.level.dimension();
        if(PENDING.size()>=8)return obj("error","server_observation_busy","reason","At most eight pending server reads; poll existing tickets");
        CompletableFuture<JsonObject> future=server.submit(()->scan(server,dimension,kind,b,offset,limit,startsOffset));
        ticket=UUID.randomUUID().toString();PENDING.put(ticket,new Pending(mc.level,kind,future,now));return pending(ticket);
    }
    private static JsonObject pending(String ticket) {
        return ObservJson.envelope(obj("status","pending","ticket",ticket,"poll","Repeat the same observation kind with ticket; expires after 60 seconds"),"integrated_server_thread","requested_loaded_chunks",false,false);
    }
    private static JsonObject scan(IntegratedServer server,ResourceKey<Level> dimension,String kind,int[] b,long offset,int budget,int startsOffset) {
        ServerLevel level=server.getLevel(dimension);
        if(level==null)return obj("error","dimension_unavailable","reason","Integrated-server dimension is no longer loaded");
        long width=(long)b[2]-b[0]+1,total=width*((long)b[3]-b[1]+1),i=offset;int examined=0,unloaded=0;boolean clipped=false;int nextStarts=0;JsonArray entries=new JsonArray();
        for(;i<total&&examined<budget;i++) {
            examined++;int x=(int)(b[0]+i%width),z=(int)(b[1]+i/width);
            LevelChunk chunk=level.getChunkSource().getChunkNow(x,z);
            if(chunk==null){unloaded++;entries.add(obj("x",x,"z",z,"loaded",false));continue;}
            JsonObject c=obj("x",x,"z",z,"loaded",true);
            if(kind.equals("chunks")) {
                c.addProperty("slime_chunk",WorldgenRandom.seedSlimeChunk(x,z,level.getSeed(),987234911L).nextInt(10)==0);
                c.addProperty("slime_scope","vanilla_seed_formula_mods_may_override_spawning");
                c.addProperty("inhabited_time",chunk.getInhabitedTime());c.addProperty("structure_start_count",chunk.getAllStarts().size());
            } else {
                JsonArray starts=new JsonArray();int count=0,skip=i==offset?startsOffset:0,index=0;
                for(var e:chunk.getAllStarts().entrySet()) {
                    if(index++<skip)continue;if(count>=Math.max(1,budget-examined)||count>=64){clipped=true;break;}count++;
                    var start=e.getValue();if(!start.isValid())continue;var bb=start.getBoundingBox();
                    var id=level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(e.getKey());
                    starts.add(obj("id",id==null?"unknown":id.toString(),"start_chunk",new int[]{start.getChunkPos().x,start.getChunkPos().z},
                        "bounds",obj("min",new int[]{bb.minX(),bb.minY(),bb.minZ()},"max",new int[]{bb.maxX(),bb.maxY(),bb.maxZ()}),"references",start.getReferences()));
                }
                examined+=count;c.add("starts",starts);c.addProperty("starts_examined",count);c.addProperty("starts_total",chunk.getAllStarts().size());
                c.addProperty("starts_offset",skip);c.addProperty("starts_truncated",skip+count<chunk.getAllStarts().size());if(skip>chunk.getAllStarts().size())throw new IllegalArgumentException("stale_starts_offset");if(skip+count<chunk.getAllStarts().size()){clipped=true;nextStarts=dev.mcpmcp.core.WithinChunkPage.next(i,skip,chunk.getAllStarts().size(),count,(int)Math.min(Integer.MAX_VALUE,total)).inner();}
                c.addProperty("scope","starts_in_loaded_chunk_not_all_structures_intersecting_region");
            }
            entries.add(c);if(nextStarts>0)break;
        }
        boolean more=i<total;
        return ObservJson.envelope(obj(kind,entries,"offset",offset,"next_offset",more?i:null,"next_starts_offset",more?nextStarts:null,"candidate_total",total,"work_units",examined,"unloaded",unloaded,
            "generation",false,"dimension",dimension.location().toString(),"per_chunk_start_limit",64,"within_chunk_truncated",clipped),
            "integrated_server_thread","loaded_server_chunk_rectangle",!more&&unloaded==0&&!clipped,more||clipped);
    }
    private ObservServer(){}
}
