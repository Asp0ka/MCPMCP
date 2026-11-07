package dev.mcpmcp.magic;
import com.google.gson.*;
final class J {
 static JsonObject obj(Object... kv){JsonObject o=new JsonObject();for(int i=0;i<kv.length;i+=2)o.add(kv[i].toString(),json(kv[i+1]));return o;}
 static JsonElement json(Object v){if(v==null)return JsonNull.INSTANCE;if(v instanceof JsonElement j)return j;if(v instanceof Number n)return new JsonPrimitive(n);if(v instanceof Boolean b)return new JsonPrimitive(b);if(v instanceof Iterable<?> it){JsonArray a=new JsonArray();it.forEach(x->a.add(json(x)));return a;}return new JsonPrimitive(v.toString());}
 static String str(JsonObject a,String k,String d){return a.has(k)?a.get(k).getAsString():d;}
 static int num(JsonObject a,String k,int d){return a.has(k)?a.get(k).getAsInt():d;}
 static JsonObject unknown(String why){return obj("status","unknown","reason",why);}
 static JsonObject known(Object v,String source){return obj("status","known","value",v,"source",source);}
 static JsonObject fail(String code){return obj("ok",false,"problem",code);}
 static Object read(Object o,String method)throws ReflectiveOperationException{return o.getClass().getMethod(method).invoke(o);}
}
