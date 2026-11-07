package dev.mcpmcp.sophisticated;

import com.google.gson.*;
import dev.mcpmcp.api.*;
import dev.mcpmcp.core.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

/** Loads each packaged provider independently; adapter classes cannot fall back to main output. */
public final class DistributionTest {
 static final class Isolated extends URLClassLoader {
  Isolated(Path jar) throws Exception {super(new URL[]{jar.toUri().toURL()},McpmcpPlugin.class.getClassLoader());}
  @Override protected Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
   if(!name.startsWith("dev.mcpmcp.sophisticated."))return super.loadClass(name,resolve);
   synchronized(getClassLoadingLock(name)){Class<?> c=findLoadedClass(name);if(c==null)c=findClass(name);if(resolve)resolveClass(c);return c;}
  }
  @Override public Enumeration<URL> getResources(String name) throws java.io.IOException {return name.equals("META-INF/services/dev.mcpmcp.api.McpmcpPlugin")?findResources(name):super.getResources(name);}
 }
 public static void main(String[] args) throws Exception {
  int count=0;Set<String> all=new HashSet<>();
  for(String key:List.of("storage","backpack","furnace")){
   String mod=Contract.namespace(key);
   JsonObject target=JsonParser.parseString(Files.readString(Path.of("evidence/targets.json"))).getAsJsonObject().getAsJsonObject(mod);
   Path shipped=Path.of("distribution",mod+"-"+target.get("sha256").getAsString().substring(0,32),"adapter.jar");
   Path built=Path.of("build/libs/"+key+"-adapter.jar");
   if(Files.mismatch(built,shipped)!=-1)throw new AssertionError("build/distribution bytes differ: "+key);
   for(Path jar:List.of(built,shipped)) {
   String expected="mcpmcp."+mod;
   try(JarFile z=new JarFile(jar.toFile())){long providers=z.stream().filter(e->e.getName().matches("dev/mcpmcp/sophisticated/.*Plugin.class")).count();if(providers!=1)throw new AssertionError("exactly one provider class: "+jar);}
   try(Isolated loader=new Isolated(jar)){
    Set<String> registrations=new HashSet<>();
    var providers=ServiceLoader.load(McpmcpPlugin.class,loader).stream().toList();if(providers.size()!=1)throw new AssertionError("exactly one service provider: "+jar);
    McpmcpPlugin plugin=providers.get(0).get();if(!plugin.id().equals(expected)||plugin.getClass().getClassLoader()!=loader)throw new AssertionError("independent provider identity");
    plugin.register(new PluginContext(){
     public void action(String n,JsonObject s,String t,String p,Handler h){
      if(!registrations.add(n))throw new AssertionError("duplicate action "+n);PluginValidation.schema(s);
      if(n.endsWith(".quick_move")||n.endsWith(".quick_move_packet")){
       JsonObject a=Jsons.obj("menu_id",1,"menu_token","observed","slot",0,"state_id",0,"expected_item","minecraft:coal","expected_count",3,"expected_stack_hash","a".repeat(64));
       Schema.validate(a,s);a.remove("expected_stack_hash");
       try{Schema.validate(a,s);throw new AssertionError("shipped route omitted full stack identity: "+n);}catch(IllegalArgumentException required){if(!required.getMessage().equals("missing_expected_stack_hash"))throw required;}
      }
     }
     public void observer(String k,int p,Handler h){if(!registrations.add("observe:"+k))throw new AssertionError("duplicate root observer");}
     public JsonObject execute(String n,JsonObject a){throw new AssertionError("no registration side effect");}
     public JsonObject observe(String k,JsonObject a){throw new AssertionError("no registration observation");}
    });
    if(registrations.size()!=Contract.operations(key).size()+1)throw new AssertionError("complete provider registrations: "+jar);
    if(jar.equals(built))for(String registration:registrations)if(!all.add(registration))throw new AssertionError("duplicate cross-plugin registration "+registration);
    count++;
   }
   }
  }
  if(count!=6||all.size()!=14)throw new AssertionError("both jar sets load 11 actions plus 3 observers");
  try(var folders=Files.list(Path.of("distribution"))){for(Path folder:folders.filter(Files::isDirectory).toList()){JsonObject m=JsonParser.parseString(Files.readString(folder.resolve("manifest.json"))).getAsJsonObject();PluginValidation.manifest(m);}}
  System.out.println("PASS DistributionTest independent_jars=3 isolated_loads="+count+" build_distribution_equal=true unique_registrations="+all.size()+" manifests=valid");
 }
}
