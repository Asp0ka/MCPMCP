package dev.mcpmcp.forge;

import com.google.gson.*;
import java.time.Instant;
import java.util.Map;
import static dev.mcpmcp.core.Jsons.*;

public final class ObservJson {
    public static JsonObject envelope(JsonObject data,String source,String scope,boolean complete,boolean truncated) {
        return obj("data",data,"source",source,"scope",scope,"observed_at",Instant.now().toString(),"complete",complete,"truncated",truncated);
    }
    public static String clipped(String s,int n) {return s.length()<=n?s:s.substring(0,n);}
    /** Bounded nodes, depth and string size; never stringify an unbounded source tree. */
    public static JsonElement bounded(JsonElement input,int nodes,int chars) {
        if(nodes<1||chars<1)throw new IllegalArgumentException("invalid_json_budget");
        return copy(input,new int[]{nodes},chars,0);
    }
    private static JsonElement copy(JsonElement in,int[] left,int chars,int depth) {
        if(left[0]--<=0||depth>8)return new JsonPrimitive("[truncated]");
        if(in==null||in.isJsonNull())return JsonNull.INSTANCE;
        if(in.isJsonPrimitive())return in.getAsJsonPrimitive().isString()?new JsonPrimitive(clipped(in.getAsString(),chars)):in.deepCopy();
        if(in.isJsonArray()) {
            JsonArray a=new JsonArray();for(JsonElement v:in.getAsJsonArray()) {if(left[0]<=0){a.add("[truncated]");break;}a.add(copy(v,left,chars,depth+1));}return a;
        }
        JsonObject o=new JsonObject();for(Map.Entry<String,JsonElement> e:in.getAsJsonObject().entrySet()) {
            if(left[0]<=0){o.addProperty("_truncated",true);break;}o.add(clipped(e.getKey(),128),copy(e.getValue(),left,chars,depth+1));
        }return o;
    }
    private ObservJson(){}
}
