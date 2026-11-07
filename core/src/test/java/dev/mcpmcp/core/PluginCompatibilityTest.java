package dev.mcpmcp.core;
import java.util.*;
public final class PluginCompatibilityTest {
 private static int checks;
 private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 public static void main(String[] args){
  var exact=PluginCompatibility.evaluate(true,"1.2","1.2","a","a",true,false);
  check(exact.active()&&exact.versionMatch()&&exact.hashMatch()&&!exact.forced(),"exact target");
  var mismatch=PluginCompatibility.evaluate(true,"1.2","1.3","a","b",true,false);
  check(!mismatch.active()&&mismatch.reason().equals("target_version_mismatch")&&mismatch.canForce(),"version mismatch");
  var forced=PluginCompatibility.evaluate(true,"1.2","1.3","a","b",true,true);
  check(forced.active()&&forced.forced()&&!forced.versionMatch()&&!forced.hashMatch(),"force retains mismatch facts");
  check(!PluginCompatibility.evaluate(false,"1.2","","a","",true,true).active(),"missing cannot force");
  check(!PluginCompatibility.evaluate(true,"1.2","1.3","a","b",false,true).canForce(),"platform cannot force");
  var legacy=PluginCompatibility.evaluate(true,"","1.2","a","a",true,false);
  check(legacy.active()&&!legacy.versionDeclared()&&!legacy.versionMatch(),"old manifests version unknown");
  check(PluginCompatibility.evaluate(true,"1.2","1.2","a","b",true,false).reason().equals("target_hash_mismatch"),"same version different jar");
  String a=PluginCompatibility.binding("plugin","folder","manifest","1.2","hash");
  check(a.matches("[a-f0-9]{64}")&&a.equals(PluginCompatibility.binding("plugin","folder","manifest","1.2","hash")),"stable binding");
  check(!a.equals(PluginCompatibility.binding("plugin","folder","changed","1.2","hash")),"manifest invalidates");
  check(!a.equals(PluginCompatibility.binding("plugin","folder","manifest","1.3","hash")),"mod version invalidates");
  check(!a.equals(PluginCompatibility.binding("plugin","folder","manifest","1.2","other")),"mod jar invalidates");
  check(PluginCompatibility.evaluate(true,"1.2","1.2","a","",true,true,false).reason().equals("target_jar_unavailable"),"unavailable jar is not platform mismatch");
  System.out.println("PluginCompatibilityTest: "+checks+" checks passed");
 }
}
