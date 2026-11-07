package dev.mcpmcp.core;
import com.google.gson.*;
public final class Jsons {
 public static final Gson GSON=new GsonBuilder().disableHtmlEscaping().create();
 public static JsonObject obj(Object... v){JsonObject o=new JsonObject();for(int i=0;i<v.length;i+=2)o.add(v[i].toString(),GSON.toJsonTree(v[i+1]));return o;}
 public static JsonObject error(String code){return obj("error",code);}
 public static String str(JsonObject o,String k,String d){if(!o.has(k))return d;JsonElement e=o.get(k);if(!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isString())throw new IllegalArgumentException("string_required_"+k);return e.getAsString();}
 public static double number(JsonObject o,String k,double d){if(!o.has(k))return d;JsonElement e=o.get(k);if(!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("number_required_"+k);double n=e.getAsDouble();if(!Double.isFinite(n))throw new IllegalArgumentException("finite_required_"+k);return n;}
 public static int integer(JsonObject o,String k,int d){double n=number(o,k,d);if(n!=Math.rint(n)||n<Integer.MIN_VALUE||n>Integer.MAX_VALUE)throw new IllegalArgumentException("integer_required_"+k);return (int)n;}
 public static boolean bool(JsonObject o,String k,boolean d){if(!o.has(k))return d;JsonElement e=o.get(k);if(!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("boolean_required_"+k);return e.getAsBoolean();}
 public static JsonObject object(JsonObject o,String k){if(!o.has(k))return new JsonObject();if(!o.get(k).isJsonObject())throw new IllegalArgumentException("object_required_"+k);return o.getAsJsonObject(k);}
 public static JsonArray array(JsonObject o,String k){if(!o.has(k))return new JsonArray();if(!o.get(k).isJsonArray())throw new IllegalArgumentException("array_required_"+k);return o.getAsJsonArray(k);}
 private Jsons(){}
}
