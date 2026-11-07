package dev.mcpmcp.curios;
import com.google.gson.*;
/** Keep explicit unknown/null fields; core Jsons.obj's default Gson omits them in nested objects. */
final class CuriosJson {
 static final Gson GSON=new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
 static JsonObject obj(Object... values){JsonObject out=new JsonObject();for(int i=0;i<values.length;i+=2)out.add(values[i].toString(),GSON.toJsonTree(values[i+1]));return out;}
 static String str(JsonObject o,String k,String fallback){return dev.mcpmcp.core.Jsons.str(o,k,fallback);}
 static int integer(JsonObject o,String k,int fallback){return dev.mcpmcp.core.Jsons.integer(o,k,fallback);}
 static boolean bool(JsonObject o,String k,boolean fallback){return dev.mcpmcp.core.Jsons.bool(o,k,fallback);}
 private CuriosJson(){}
}
