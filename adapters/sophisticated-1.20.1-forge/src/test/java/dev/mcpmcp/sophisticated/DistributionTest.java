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
   Path jar=Path.of("build/libs/"+key+"-adapter.jar");String expected="mcpmcp."+Contract.namespace(key);
   try(JarFile z=new JarFile(jar.toFile())){long providers=z.stream().filter(e->e.getName().matches("dev/mcpmcp/sophisticated/.*Plugin.class")).count();if(providers!=1)throw new AssertionError("exactly one provider class: "+jar);}
   try(Isolated loader=new Isolated(jar)){
    var providers=ServiceLoader.load(McpmcpPlugin.class,loader).stream().toList();if(providers.size()!=1)throw new AssertionError("exactly one service provider: "+jar);
    McpmcpPlugin plugin=providers.get(0).get();if(!plugin.id().equals(expected)||plugin.getClass().getClassLoader()!=loader)throw new AssertionError("independent provider identity");
    plugin.register(new PluginContext(){
     public void action(String n,JsonObject s,String t,String p,Handler h){if(!all.add(n))throw new AssertionError("duplicate cross-plugin action "+n);PluginValidation.schema(s);}
     public void observer(String k,int p,Handler h){if(!all.add("observe:"+k))throw new AssertionError("duplicate root observer");}
     public JsonObject execute(String n,JsonObject a){throw new AssertionError("no registration side effect");}
     public JsonObject observe(String k,JsonObject a){throw new AssertionError("no registration observation");}
    });count++;
   }
  }
  if(all.size()!=14)throw new AssertionError("11 actions plus 3 observers");
  try(var folders=Files.list(Path.of("distribution"))){for(Path folder:folders.filter(Files::isDirectory).toList()){JsonObject m=JsonParser.parseString(Files.readString(folder.resolve("manifest.json"))).getAsJsonObject();PluginValidation.manifest(m);}}
  System.out.println("PASS DistributionTest independent_jars="+count+" unique_registrations="+all.size()+" manifests=valid");
 }
}
