package dev.mcpmcp.core;
import com.google.gson.*;import java.lang.reflect.*;
public final class PluginValidationTest {
 static int checks;
 static JsonObject parse(String s){return JsonParser.parseString(s.replace((char)39,(char)34)).getAsJsonObject();}
 static void validate(JsonObject a)throws Exception{try{Class.forName("dev.mcpmcp.core.PluginValidation").getMethod("action",JsonObject.class).invoke(null,a);}catch(InvocationTargetException e){if(e.getCause() instanceof IllegalArgumentException x)throw x;throw e;}catch(ClassNotFoundException e){throw new AssertionError("missing full plugin preflight",e);}}
 static void bad(JsonObject a)throws Exception{try{validate(a);throw new AssertionError("invalid declaration accepted");}catch(IllegalArgumentException expected){checks++;}}
 public static void main(String[] args)throws Exception {
 JsonObject a=parse("{'name':'test.select','input_schema':{'type':'object','properties':{},'additionalProperties':false}}");bad(a);
 a.add("packet",parse("{'packet_class':'example.Packet','signature':['int'],'values':[1]}"));validate(a);checks++;
 JsonObject b=a.deepCopy();b.remove("input_schema");bad(b);b=a.deepCopy();b.addProperty("typo",true);bad(b);
 b=a.deepCopy();b.getAsJsonObject("packet").add("packet_class",parse("{'param':'class'}"));bad(b);
 b=a.deepCopy();b.getAsJsonObject("packet").getAsJsonArray("signature").set(0,parse("{'param':'type'}"));bad(b);
 b=a.deepCopy();b.getAsJsonObject("packet").getAsJsonArray("values").set(0,parse("{'param':'undeclared'}"));bad(b);
 b=a.deepCopy();b.add("verify",JsonParser.parseString("[{'kind':'unknown'}]".replace((char)39,(char)34)));bad(b);
 b=a.deepCopy();b.add("preconditions",JsonParser.parseString("[{'kind':'menu','class':''}]".replace((char)39,(char)34)));bad(b);
 b=a.deepCopy();b.getAsJsonObject("input_schema").addProperty("type","unsupported");bad(b);
 b=a.deepCopy();b.add("verify",JsonParser.parseString("[{'kind':'inventory','item_id':'minecraft:stone','min_count':1}]".replace((char)39,(char)34)));validate(b);checks++;
 b=a.deepCopy();b.getAsJsonObject("packet").getAsJsonArray("values").set(0,new JsonPrimitive("not an integer"));bad(b);
 b=a.deepCopy();b.getAsJsonObject("input_schema").add("properties",parse("{'slot':{'type':'string'}}"));b.getAsJsonObject("packet").getAsJsonArray("values").set(0,parse("{'param':'slot'}"));bad(b);
 b=a.deepCopy();b.getAsJsonObject("input_schema").addProperty("minLength",5);b.getAsJsonObject("input_schema").addProperty("maxLength",2);bad(b);
 JsonObject manifest=parse("{'api':1,'id':'example','mod_id':'minecraft','target_version':'1.20.1'}");PluginValidation.manifest(manifest);checks++;manifest.remove("target_version");PluginValidation.manifest(manifest);checks++;
 System.out.println("PluginValidationTest passed "+checks);
 }
}

