package dev.mcpmcp.core;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
/** Compatibility facts are independent from the local user's override decision. */
public final class PluginCompatibility {
 public record Result(boolean installed,boolean versionDeclared,boolean versionMatch,boolean hashMatch,boolean canForce,boolean forced,boolean active,String reason){}
 public static Result evaluate(boolean installed,String expectedVersion,String actualVersion,String expectedHash,String actualHash,boolean platform,boolean override){
  return evaluate(installed,expectedVersion,actualVersion,expectedHash,actualHash,platform,override,true);
 }
 public static Result evaluate(boolean installed,String expectedVersion,String actualVersion,String expectedHash,String actualHash,boolean platform,boolean override,boolean jarAvailable){
  boolean declared=!expectedVersion.isEmpty(),version=declared&&expectedVersion.equals(actualVersion),hash=expectedHash.isEmpty()||expectedHash.equals(actualHash);
  boolean mismatch=declared&&!version||!hash,can=installed&&platform&&jarAvailable&&mismatch;
  String reason=!installed?"target_mod_missing":!platform?"platform_mismatch":!jarAvailable?"target_jar_unavailable":declared&&!version?"target_version_mismatch":!hash?"target_hash_mismatch":"active";
  boolean forced=can&&override;return new Result(installed,declared,version,hash,can,forced,installed&&platform&&jarAvailable&&(!mismatch||forced),forced?"forced":reason);
 }
 public static String binding(String plugin,String folder,String declarationDigest,String version,String hash){try{MessageDigest d=MessageDigest.getInstance("SHA-256");for(String s:new String[]{plugin,folder,declarationDigest,version,hash}){byte[] b=s.getBytes(StandardCharsets.UTF_8);d.update(java.nio.ByteBuffer.allocate(4).putInt(b.length).array());d.update(b);}return HexFormat.of().formatHex(d.digest());}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 private PluginCompatibility(){}
}
