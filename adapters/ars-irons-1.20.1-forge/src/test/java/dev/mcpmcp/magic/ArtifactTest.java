package dev.mcpmcp.magic;
import dev.mcpmcp.api.McpmcpPlugin;
import java.nio.file.*;
import java.net.*;
import java.util.*;
import java.util.jar.*;
public final class ArtifactTest {
 public static void main(String[] args)throws Exception {
  int count=0;
  for(String arg:args){Path jar=Path.of(arg);String expected=jar.getFileName().toString().replace("-adapter.jar","");
   try(JarFile archive=new JarFile(jar.toFile())){
    String service=new String(archive.getInputStream(archive.getJarEntry("META-INF/services/dev.mcpmcp.api.McpmcpPlugin")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
    if(service.lines().filter(s->!s.isBlank()).count()!=1)throw new AssertionError("one provider");
    if(archive.stream().anyMatch(e->e.getName().startsWith("dev/mcpmcp/api/")||e.getName().startsWith("dev/mcpmcp/core/")||e.getName().startsWith("net/minecraft/")||e.getName().startsWith("com/google/gson/")))throw new AssertionError("bundled host classes");
   }
   try(URLClassLoader loader=new URLClassLoader(new URL[]{jar.toUri().toURL()},McpmcpPlugin.class.getClassLoader())){
    List<McpmcpPlugin> found=new ArrayList<>();ServiceLoader.load(McpmcpPlugin.class,loader).forEach(found::add);
    if(found.size()!=1||!found.get(0).id().equals("mcpmcp."+expected))throw new AssertionError("provider identity");
    System.out.println("ServiceLoader PASS "+expected+" "+found.get(0).getClass().getName());count++;
   }
  }
  if(count!=3)throw new AssertionError("three independent jars");
  System.out.println("ArtifactTest PASS jars="+count+"; no game execution");
 }
}
