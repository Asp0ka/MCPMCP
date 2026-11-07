package dev.mcpmcp.core;
import com.google.gson.*;
import java.util.*;
public final class Schema {
 public static void validate(JsonElement value,JsonObject schema){walk(value,schema,0,new int[]{4096});}
 private static void walk(JsonElement v,JsonObject s,int depth,int[] budget){
  if(depth>16||--budget[0]<0)throw new IllegalArgumentException("json_budget_exceeded");
  String type=Jsons.str(s,"type","any");
  switch(type){
   case "object" -> {if(!v.isJsonObject())bad();JsonObject o=v.getAsJsonObject(),p=Jsons.object(s,"properties");if(o.size()>Jsons.integer(s,"maxProperties",128))bad();for(JsonElement r:Jsons.array(s,"required"))if(!o.has(r.getAsString()))throw new IllegalArgumentException("missing_"+r.getAsString());
    for(var e:o.entrySet()){if(e.getKey().length()>256)bad();if(p.has(e.getKey()))walk(e.getValue(),p.getAsJsonObject(e.getKey()),depth+1,budget);else if(s.has("additionalProperties")&&s.get("additionalProperties").isJsonObject())walk(e.getValue(),s.getAsJsonObject("additionalProperties"),depth+1,budget);else if(!Jsons.bool(s,"additionalProperties",false))throw new IllegalArgumentException("unknown_"+e.getKey());else bounded(e.getValue(),depth+1,budget);}}
   case "array" -> {if(!v.isJsonArray()||v.getAsJsonArray().size()<Jsons.integer(s,"minItems",0)||v.getAsJsonArray().size()>Jsons.integer(s,"maxItems",128))bad();for(JsonElement e:v.getAsJsonArray())walk(e,Jsons.object(s,"items"),depth+1,budget);}
   case "string" -> {if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isString())bad();String x=v.getAsString();if(x.length()<Jsons.integer(s,"minLength",0)||x.length()>Jsons.integer(s,"maxLength",8192))bad();if(s.has("pattern")&&!x.matches(s.get("pattern").getAsString()))bad();}
   case "integer","number" -> {if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber())bad();double n=v.getAsDouble();if(!Double.isFinite(n)||n<Jsons.number(s,"minimum",-1e30)||n>Jsons.number(s,"maximum",1e30)||(type.equals("integer")&&n!=Math.rint(n)))bad();}
   case "boolean" -> {if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isBoolean())bad();}
   case "null" -> {if(!v.isJsonNull())bad();}
   case "any" -> bounded(v,depth,budget);
   default -> throw new IllegalArgumentException("unsupported_schema_type");
  }
  if(s.has("enum")){boolean found=false;for(JsonElement e:s.getAsJsonArray("enum"))found|=e.equals(v);if(!found)bad();}
 }
 public static void bounded(JsonElement v){bounded(v,0,new int[]{4096});}
 private static void bounded(JsonElement v,int depth,int[] budget){if(depth>16||--budget[0]<0)bad();if(v.isJsonObject()){if(v.getAsJsonObject().size()>128)bad();for(var e:v.getAsJsonObject().entrySet()){if(e.getKey().length()>256)bad();bounded(e.getValue(),depth+1,budget);}}else if(v.isJsonArray()){if(v.getAsJsonArray().size()>512)bad();for(JsonElement e:v.getAsJsonArray())bounded(e,depth+1,budget);}else if(v.isJsonPrimitive()){if(v.getAsJsonPrimitive().isString()&&v.getAsString().length()>32768)bad();if(v.getAsJsonPrimitive().isNumber()&&!Double.isFinite(v.getAsDouble()))bad();}}
 private static void bad(){throw new IllegalArgumentException("schema_validation_failed");}
}
