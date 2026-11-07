package dev.mcpmcp.core;
import java.util.Set;
public final class Policy {
 public static boolean allowed(String mode,String chosen,String actual,String permission){
  if(!Set.of("NORMAL","HACK","CHEAT","HACK_CHEAT").contains(mode)||!Set.of("PACKET","SIMULATE","MIXED").contains(chosen))return false;
  if(!Set.of("normal","hack","cheat").contains(permission))return false;
  if(permission.equals("hack")&&!Set.of("HACK","HACK_CHEAT").contains(mode))return false;
  if(permission.equals("cheat")&&!Set.of("CHEAT","HACK_CHEAT").contains(mode))return false;
  if(chosen.equals("PACKET"))return actual.equals("packet");
  if(chosen.equals("SIMULATE"))return Set.of("input","client","baritone").contains(actual);
  return Set.of("input","client","packet","baritone").contains(actual);
 }
 private Policy(){}
}
