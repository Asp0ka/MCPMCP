package dev.mcpmcp.forge;

import com.google.gson.*;
import java.lang.reflect.*;
import java.util.*;

/** Dependency-light contract tests. Run main with Gson; no game, Forge or JUnit needed. */
public final class ObservContractTest {
    private static int checks;
    private static void check(boolean ok, String name) { if (!ok) throw new AssertionError(name); checks++; }
    private static Object call(String type, String method, Class<?>[] types, Object... args) throws Exception {
        try { return Class.forName("dev.mcpmcp.forge." + type).getMethod(method, types).invoke(null, args); }
        catch (ClassNotFoundException e) { throw new AssertionError("missing behavior: " + type, e); }
        catch (InvocationTargetException e) {
            if (e.getCause() instanceof IllegalArgumentException x) throw x;
            throw e;
        }
    }
    private static void rejects(String type, String method, Class<?>[] types, Object... args) throws Exception {
        try { call(type, method, types, args); throw new AssertionError("accepted invalid " + method); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    private static void bounds() throws Exception {
        check((int)call("ObservBounds", "budget", new Class[]{int.class}, 9000) == 4096, "hard scan ceiling");
        check((int)call("ObservBounds", "budget", new Class[]{int.class}, 0) == 1, "nonzero budget");
        Class<?>[] six = {int.class,int.class,int.class,int.class,int.class,int.class,long.class};
        check(Arrays.equals((int[])call("ObservBounds", "position", six, -1,-2,-3,1,0,-2,0L), new int[]{-1,-2,-3}), "negative box origin");
        check(Arrays.equals((int[])call("ObservBounds", "position", six, -1,-2,-3,1,0,-2,17L), new int[]{1,0,-2}), "last coordinate and no duplicate");
        rejects("ObservBounds", "position", six, -1,-2,-3,1,0,-2,18L);
        rejects("ObservBounds", "volume", new Class[]{int.class,int.class,int.class,int.class,int.class,int.class}, 2,0,0,1,0,0);
        check((long)call("ObservBounds", "volume", new Class[]{int.class,int.class,int.class,int.class,int.class,int.class}, -30000000,-64,-30000000,30000000,319,30000000) > Integer.MAX_VALUE, "no volume overflow");
    }
    private static void chat() throws Exception {
        Class<?> c;
        try { c=Class.forName("dev.mcpmcp.forge.ChatHistory"); }
        catch (ClassNotFoundException e) { throw new AssertionError("missing behavior: ChatHistory", e); }
        Object h=c.getConstructor(int.class,int.class).newInstance(2,12);
        Method add=c.getMethod("add",String.class,String.class,long.class);
        add.invoke(h,"one","chat",1L); add.invoke(h,"two","system",2L); add.invoke(h,"three is a very long message","chat",3L);
        JsonObject page=(JsonObject)c.getMethod("read",long.class,int.class).invoke(h,0L,1);
        check(page.getAsJsonArray("entries").size()==1,"bounded chat page");
        check(page.get("dropped_before").getAsLong()==1,"dropped sequence explicit");
        check(page.get("next_after").getAsLong()==2,"sequence cursor");
        JsonObject last=(JsonObject)c.getMethod("read",long.class,int.class).invoke(h,2L,99);
        check(last.getAsJsonArray("entries").get(0).getAsJsonObject().get("text").getAsString().length()<=12,"chat message ceiling");
        check(last.getAsJsonArray("entries").get(0).getAsJsonObject().get("truncated").getAsBoolean(),"message truncation explicit");
        JsonObject empty=(JsonObject)c.getMethod("read",long.class,int.class).invoke(h,3L,10);
        check(empty.getAsJsonArray("entries").isEmpty(),"no repeat after sequence");
    }
    private static void cursor() throws Exception {
        Class<?> c;
        try { c=Class.forName("dev.mcpmcp.forge.ObservCursor"); }
        catch (ClassNotFoundException e) { throw new AssertionError("missing behavior: ObservCursor", e); }
        Object v=c.getConstructor(Iterator.class).newInstance(List.of(1,2,3,4).iterator());
        Method page=c.getMethod("take",int.class);
        check(page.invoke(v,2).equals(List.of(1,2)),"bounded candidate page");
        check(page.invoke(v,1).equals(List.of(3)),"iterator resumes without rescanning prefix");
        check(page.invoke(v,4096).equals(List.of(4)),"last page");
        check(!(boolean)c.getMethod("hasNext").invoke(v),"cursor exhausted");
        rejects("ObservBounds","pageSize",new Class[]{int.class,int.class},-1,4096);
    }
    private static void ui() throws Exception {
        Class<?>[] key={String.class};
        check((int)call("UiArguments","key",key,"ENTER")==257,"named GLFW key");
        check((int)call("UiArguments","key",key,"a")==65,"letter key");
        rejects("UiArguments","key",key,"EXECUTE_METHOD");
        check((String)call("UiArguments","worldId",key,"Test World")!=null,"normal save id");
        for(String x: List.of("../save","C:/save","a\\b",".","..","")) rejects("UiArguments","worldId",key,x);
        rejects("UiArguments","text",key,"x".repeat(8193));
        rejects("UiArguments","coordinate",new Class[]{double.class,int.class},Double.NaN,1920);
        rejects("UiArguments","coordinate",new Class[]{double.class,int.class},1920d,1920);
        check((double)call("UiArguments","coordinate",new Class[]{double.class,int.class},1919d,1920)==1919d,"screen edge");
    }
    private static void json() throws Exception {
        JsonObject source=new JsonObject();source.addProperty("secretly_large","abc".repeat(30));
        JsonArray values=new JsonArray();for(int i=0;i<100;i++)values.add(i);source.add("values",values);
        JsonObject copy=(JsonObject)call("ObservJson","bounded",new Class[]{JsonElement.class,int.class,int.class},source,8,12);
        check(copy.toString().length()<240,"bounded JSON expansion");
        check(copy.get("secretly_large").getAsString().length()<=12,"bounded JSON string");
        JsonObject envelope=(JsonObject)call("ObservJson","envelope",new Class[]{JsonObject.class,String.class,String.class,boolean.class,boolean.class},new JsonObject(),"client","test",false,true);
        for(String field:List.of("data","source","scope","observed_at","complete","truncated"))check(envelope.has(field),"envelope "+field);
        check(!envelope.get("complete").getAsBoolean(),"incomplete honest");
    }
    private static void recipes() throws Exception {
        Class<?>[] types={JsonObject.class,String.class,String.class,String.class,String.class};
        JsonObject args=new JsonObject();args.addProperty("query","PLANK");args.addProperty("type","minecraft:crafting");
        check((boolean)call("RecipeQuery","matches",types,args,"minecraft:oak_planks","minecraft:crafting","minecraft:oak_planks","Oak Planks"),"recipe case insensitive filter");
        check(!(boolean)call("RecipeQuery","matches",types,args,"minecraft:iron_ingot","minecraft:smelting","minecraft:iron_ingot","Iron Ingot"),"recipe type exclusion");
        args=new JsonObject();args.addProperty("output","minecraft:stick");
        check(!(boolean)call("RecipeQuery","matches",types,args,"minecraft:stick","minecraft:crafting","minecraft:oak_planks","Oak Planks"),"output means item not recipe id");
    }
    public static void main(String[] args) throws Exception {
        String group=args.length==0?"all":args[0];
        if(group.equals("all")||group.equals("bounds"))bounds();
        if(group.equals("all")||group.equals("chat"))chat();
        if(group.equals("all")||group.equals("cursor"))cursor();
        if(group.equals("all")||group.equals("ui"))ui();
        if(group.equals("all")||group.equals("json"))json();
        if(group.equals("all")||group.equals("recipes"))recipes();
        System.out.println("ObservContractTest: "+checks+" checks passed ("+group+")");
    }
}
