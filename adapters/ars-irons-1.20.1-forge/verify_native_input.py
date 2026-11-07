"""Exercise real pinned Iron's input/packet classes with offline host seams.
No Minecraft is launched; intercepted packets prove local dispatch, not server acceptance.
"""
from pathlib import Path
import hashlib,json,re,shutil,subprocess,zipfile
ROOT=Path(__file__).resolve().parent
JDK=Path('D:/Games/mc/mc-info-mcp/tooling/jdk17/jdk-17.0.21+9/bin')
TARGET=json.loads((ROOT/'targets.json').read_text(encoding='utf8'))['irons_spellbooks']
IRON=Path(TARGET['path'])
assert hashlib.sha256(IRON.read_bytes()).hexdigest()==TARGET['sha256']
WORK=ROOT/'build/native-bytecode-test'
SRC=WORK/'src';OUT=WORK/'classes';SRC.mkdir(parents=True,exist_ok=True);OUT.mkdir(parents=True,exist_ok=True)
SOURCES={
'com/mojang/blaze3d/platform/InputConstants.java':'''package com.mojang.blaze3d.platform;
public class InputConstants {public enum Type {KEYSYM,MOUSE,SCANCODE;public Key m_84895_(int n){return new Key(this,n);}}
public static class Key {final Type t;final int n;public Key(Type t,int n){this.t=t;this.n=n;}public int m_84873_(){return n;}public Type m_84868_(){return t;}}}''',
'net/minecraftforge/client/settings/IKeyConflictContext.java':'''package net.minecraftforge.client.settings; public interface IKeyConflictContext {}''',
'net/minecraft/client/KeyMapping.java':'''package net.minecraft.client;
import com.mojang.blaze3d.platform.InputConstants.*;import net.minecraftforge.client.settings.IKeyConflictContext;
public class KeyMapping {private boolean f_90817_;private int f_90818_;private Key key;public int presses,releases;
public KeyMapping(String s,IKeyConflictContext c,Key k,String category){key=k;}
public boolean m_90857_(){return f_90817_;}public boolean m_90859_(){if(f_90818_>0){f_90818_--;return true;}return false;}
public void m_7249_(boolean b){f_90817_=b;if(b)presses++;else releases++;}
public Key m_90861_(){return key;}public Key getKey(){return key;}
public void fixtureQueueClick(){f_90818_++;}}''',
'net/minecraftforge/fml/util/ObfuscationReflectionHelper.java':'''package net.minecraftforge.fml.util;import java.lang.reflect.*;public class ObfuscationReflectionHelper {public static Field findField(Class<?> c,String s){try{Field f=c.getDeclaredField(s);f.setAccessible(true);return f;}catch(Exception e){throw new RuntimeException(e);}}}''',
'net/minecraftforge/event/TickEvent.java':'''package net.minecraftforge.event;public class TickEvent {public enum Phase {START,END} public static class ClientTickEvent {public ClientTickEvent(Phase p){}}}''',
'net/minecraftforge/client/event/InputEvent.java':'''package net.minecraftforge.client.event;public class InputEvent {public static class MouseScrollingEvent {public double getScrollDelta(){return 0;}public void setCanceled(boolean b){}}public static class InteractionKeyMappingTriggered {public boolean isUseItem(){return false;}public boolean isAttack(){return false;}public void setSwingHand(boolean b){}public void setCanceled(boolean b){}}}''',
'net/minecraft/client/Options.java':'''package net.minecraft.client;import com.mojang.blaze3d.platform.InputConstants.*;public class Options {public KeyMapping f_92090_=new KeyMapping("tip",null,new Key(Type.KEYSYM,0),"");public KeyMapping f_92095_=new KeyMapping("use",null,new Key(Type.MOUSE,1),"");}''',
'net/minecraft/client/Minecraft.java':'''package net.minecraft.client;public class Minecraft {private static final Minecraft M=new Minecraft();public Options f_91066_=new Options();public static Minecraft m_91087_(){return M;}public com.mojang.blaze3d.platform.Window m_91268_(){return new com.mojang.blaze3d.platform.Window();}}''',
'com/mojang/blaze3d/platform/Window.java':'''package com.mojang.blaze3d.platform;public class Window {public long m_85439_(){return 0;}}''',
'org/lwjgl/glfw/GLFW.java':'''package org.lwjgl.glfw;public class GLFW {public static int glfwGetKey(long w,int k){return 0;}public static int glfwGetMouseButton(long w,int k){return 0;}}''',
'net/minecraft/util/Mth.java':'''package net.minecraft.util;public class Mth {public static int m_14045_(int a,int lo,int hi){return Math.max(lo,Math.min(hi,a));}}''',
'net/minecraft/world/entity/player/Player.java':'''package net.minecraft.world.entity.player;public class Player {}''',
'io/redspace/ironsspellbooks/util/MinecraftInstanceHelper.java':'''package io.redspace.ironsspellbooks.util;public class MinecraftInstanceHelper {public static net.minecraft.world.entity.player.Player getPlayer(){return null;}}''',
'io/redspace/ironsspellbooks/api/magic/SpellSelectionManager.java':'''package io.redspace.ironsspellbooks.api.magic;import java.util.*;public class SpellSelectionManager {public int getSpellCount(){return 0;}public List<Object> getAllSpells(){return List.of();}public int getSelectionIndex(){return 0;}public void makeSelection(int n){throw new AssertionError("unexpected selection change");}}''',
'io/redspace/ironsspellbooks/player/ClientMagicData.java':'''package io.redspace.ironsspellbooks.player;public class ClientMagicData {public static io.redspace.ironsspellbooks.api.magic.SpellSelectionManager getSpellSelectionManager(){return null;}public static boolean isCasting(){return false;}}''',
'io/redspace/ironsspellbooks/player/ClientSpellCastHelper.java':'''package io.redspace.ironsspellbooks.player;public class ClientSpellCastHelper {public static boolean shouldSuppressRightClicks(){return false;}public static void setSuppressRightClicks(boolean b){}}''',
'io/redspace/ironsspellbooks/player/KeyMappings.java':'''package io.redspace.ironsspellbooks.player;
import java.util.*;import com.mojang.blaze3d.platform.InputConstants;
public class KeyMappings {static{dev.mcpmcp.magic.DependencyProbe.keyInitializations++;}static ExtendedKeyMapping key(){return new ExtendedKeyMapping("fixture",null,InputConstants.Type.KEYSYM,-1,"fixture");}
public static final ExtendedKeyMapping SPELLBOOK_CAST_ACTIVE_KEYMAP=key(),SPELL_WHEEL_KEYMAP=key(),SPELL_WHEEL_TOGGLE_KEYMAP=key(),SPELLBAR_SCROLL_MODIFIER_KEYMAP=key();
public static final List<ExtendedKeyMapping> QUICK_CAST_MAPPINGS=new ArrayList<>();static{for(int i=0;i<15;i++)QUICK_CAST_MAPPINGS.add(key());}}''',
'io/redspace/ironsspellbooks/gui/overlays/SpellWheelOverlay.java':'''package io.redspace.ironsspellbooks.gui.overlays;public class SpellWheelOverlay {public static final SpellWheelOverlay instance=new SpellWheelOverlay();public boolean active;public void open(){throw new AssertionError("unexpected wheel open");}public void close(){throw new AssertionError("unexpected wheel close");}}''',
'io/redspace/ironsspellbooks/setup/PacketDistributor.java':'''package io.redspace.ironsspellbooks.setup;import java.util.*;public class PacketDistributor {public static List<Object> sent=new ArrayList<>();public static boolean fail;public static void sendToServer(Object p){if(fail)throw new IllegalStateException("fixture send failure");sent.add(p);}}''',
'io/redspace/ironsspellbooks/api/backwards_compat/CustomPacketPayload.java':'''package io.redspace.ironsspellbooks.api.backwards_compat;public interface CustomPacketPayload {}''',
'net/minecraft/network/FriendlyByteBuf.java':'''package net.minecraft.network;public class FriendlyByteBuf {public int readInt(){return 0;}public net.minecraft.network.FriendlyByteBuf writeInt(int x){return this;}}''',
'net/minecraftforge/network/NetworkEvent.java':'''package net.minecraftforge.network;public class NetworkEvent {public static class Context {public java.util.concurrent.CompletableFuture<Void> enqueueWork(Runnable r){throw new AssertionError("server must never execute in fixture");}public net.minecraft.server.level.ServerPlayer getSender(){throw new AssertionError("no server");}}}''',
'net/minecraft/server/level/ServerPlayer.java':'''package net.minecraft.server.level;public class ServerPlayer {}''',
'io/redspace/ironsspellbooks/api/util/Utils.java':'''package io.redspace.ironsspellbooks.api.util;public class Utils {public static boolean serverSideInitiateCast(net.minecraft.server.level.ServerPlayer p){throw new AssertionError("no server");}public static boolean serverSideInitiateQuickCast(net.minecraft.server.level.ServerPlayer p,int i){throw new AssertionError("no server");}}''',
'dev/mcpmcp/magic/NativeBytecodeTest.java':'''package dev.mcpmcp.magic;
import java.util.*;import java.nio.file.*;import com.google.gson.*;import static dev.mcpmcp.core.Jsons.*;import io.redspace.ironsspellbooks.player.*;import io.redspace.ironsspellbooks.setup.PacketDistributor;import io.redspace.ironsspellbooks.network.casting.*;import net.minecraftforge.event.TickEvent;import net.minecraftforge.fml.ModList;
public class NativeBytecodeTest {
static int n;static void check(boolean b,String why){n++;if(!b)throw new AssertionError(why);}
static JsonObject args(String mod){return obj("expected_spell",mod+":fixture","expected_source","mainhand","expected_source_index",0);}
static JsonObject selection(String mod){return obj("source","mainhand","source_index",0,"spell",obj("spell_id",mod+":fixture"));}
static void tick(){ClientInputEvents.onClientTick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));}
public static void main(String[] argv)throws Exception {
ModList.path=Path.of(argv[0]);
for(String mod:List.of("traveloptics","irons_spellbooks")){
 PacketDistributor.sent.clear();var a=args(mod);var s=selection(mod);var target=KeyMappings.SPELLBOOK_CAST_ACTIVE_KEYMAP;
 var otherModSameKey=new net.minecraft.client.KeyMapping("other-mod",null,target.getKey(),"");
 var r=IronsNativeInput.submit(mod,"cast",a,s);check(r.get("status").getAsString().equals("submitted"),"unbound/native cast accepted");
 check(PacketDistributor.sent.size()==1&&PacketDistributor.sent.get(0).getClass()==CastPacket.class,"one real CastPacket");
 check(!target.m_90857_()&&!target.consume()&&otherModSameKey.presses==0,"released, no pending pulse, other mod untouched");tick();tick();check(PacketDistributor.sent.size()==1,"subsequent normal ticks do not repeat cast");
 PacketDistributor.sent.clear();a.addProperty("index",3);r=IronsNativeInput.submit(mod,"quick_cast",a,s);
 check(PacketDistributor.sent.size()==1&&PacketDistributor.sent.get(0).getClass()==QuickCastPacket.class,"one real QuickCastPacket");
 var slot=QuickCastPacket.class.getDeclaredField("slot");slot.setAccessible(true);check(slot.getInt(PacketDistributor.sent.get(0))==3,"exact quick-cast packet index");tick();check(PacketDistributor.sent.size()==1,"no quick cast replay");
 PacketDistributor.sent.clear();a.addProperty("expected_source_index",1);r=IronsNativeInput.submit(mod,"cast",a,s);check(r.has("error")&&PacketDistributor.sent.isEmpty(),"changed identity sends none");a.addProperty("expected_source_index",0);
 var busy=KeyMappings.QUICK_CAST_MAPPINGS.get(0);busy.m_7249_(true);busy.m_7249_(false);r=IronsNativeInput.submit(mod,"cast",a,s);
 check(r.get("error").getAsString().equals("native_input_busy")&&PacketDistributor.sent.isEmpty()&&busy.consume(),"existing pending input preserved");
 busy.fixtureQueueClick();r=IronsNativeInput.submit(mod,"cast",a,s);check(r.has("error")&&busy.m_90859_(),"existing queued click preserved");
 busy.m_7249_(true);r=IronsNativeInput.submit(mod,"cast",a,s);check(r.has("error")&&busy.m_90857_(),"held input preserved");busy.m_7249_(false);busy.consume();
 a.addProperty("index",15);r=IronsNativeInput.submit(mod,"quick_cast",a,s);check(r.get("error").getAsString().equals("quick_cast_key_not_available")&&PacketDistributor.sent.isEmpty(),"quick cast bounds real mapping list");
 PacketDistributor.fail=true;r=IronsNativeInput.submit(mod,"cast",a,s);PacketDistributor.fail=false;
 check(r.get("delivery").getAsString().equals("unknown")&&!target.m_90857_()&&!target.consume(),"handler failure cleans owned input");tick();check(PacketDistributor.sent.isEmpty(),"failed call cannot replay later");
}
check(ModList.inspections==1&&ModList.fileReads==1,"successful dependency verification cached across casts and both providers");
ModList.fail=true;
var cached=IronsNativeInput.submit("traveloptics","cast",args("traveloptics"),selection("traveloptics"));
check(cached.has("status")&&ModList.inspections==1,"successful immutable JVM binding needs no later metadata/JAR access");
System.out.println("NativeBytecodeTest PASS assertions="+n+"; actual target ClientInputEvents/ExtendedKeyMapping/CastPacket/QuickCastPacket; host seams only; no game/server execution");}}
'''
}
# Forge metadata seams supply the actual pinned full JAR (or a deliberate failure),
# without installing any other optional provider or launching Forge/Minecraft.
SOURCES.update({
'dev/mcpmcp/magic/DependencyProbe.java':'''package dev.mcpmcp.magic;public class DependencyProbe {public static int keyInitializations;}''',
'org/apache/maven/artifact/versioning/ArtifactVersion.java':'''package org.apache.maven.artifact.versioning;public interface ArtifactVersion {}''',
'net/minecraftforge/forgespi/language/IModInfo.java':'''package net.minecraftforge.forgespi.language;public interface IModInfo {org.apache.maven.artifact.versioning.ArtifactVersion getVersion();}''',
'net/minecraftforge/forgespi/language/IModFileInfo.java':'''package net.minecraftforge.forgespi.language;public interface IModFileInfo {net.minecraftforge.forgespi.locating.IModFile getFile();}''',
'net/minecraftforge/forgespi/locating/IModFile.java':'''package net.minecraftforge.forgespi.locating;public interface IModFile {java.nio.file.Path getFilePath();}''',
'net/minecraftforge/fml/ModContainer.java':'''package net.minecraftforge.fml;public class ModContainer {public net.minecraftforge.forgespi.language.IModInfo getModInfo(){return ()->new org.apache.maven.artifact.versioning.ArtifactVersion(){public String toString(){return ModList.version;}};}}''',
'net/minecraftforge/fml/ModList.java':'''package net.minecraftforge.fml;
import java.nio.file.*;import java.util.*;import net.minecraftforge.forgespi.language.*;
public class ModList {
 public static String version="1.20.1-3.16.3";public static Path path;public static boolean installed=true,fail;public static int inspections,fileReads;
 private static final ModList INSTANCE=new ModList();
 public static ModList get(){inspections++;if(fail)throw new IllegalStateException("fixture metadata read failure");return INSTANCE;}
 public boolean isLoaded(String id){if(!id.equals("irons_spellbooks"))throw new AssertionError("wrong dependency");return installed;}
 public Optional<ModContainer> getModContainerById(String id){return installed?Optional.of(new ModContainer()):Optional.empty();}
 public IModFileInfo getModFileById(String id){fileReads++;return ()->()->path;}
}''',
'dev/mcpmcp/magic/NativeDependencyTest.java':'''package dev.mcpmcp.magic;
import java.nio.file.*;import java.util.*;import com.google.gson.*;import net.minecraftforge.fml.ModList;
import io.redspace.ironsspellbooks.player.*;import io.redspace.ironsspellbooks.setup.PacketDistributor;
import static dev.mcpmcp.core.Jsons.*;
public class NativeDependencyTest {
 static int n;static void check(boolean b,String why){n++;if(!b)throw new AssertionError(why);}
 public static void main(String[] argv)throws Exception {
  String scenario=argv[0];ModList.path=Path.of(argv[1]);
  switch(scenario){case "missing"->ModList.installed=false;case "version"->ModList.version="1.20.1-3.16.4";case "metadata"->ModList.fail=true;}
  String expected=switch(scenario){case "missing"->"dependency_mod_missing";case "version"->"dependency_version_mismatch";case "hash"->"dependency_hash_mismatch";default->"dependency_verification_failed";};
  for(String mod:List.of("traveloptics","irons_spellbooks"))for(String action:List.of("cast","quick_cast")){
   JsonObject a=NativeBytecodeTest.args(mod);a.addProperty("index",3);
   JsonObject r=IronsNativeInput.submit(mod,action,a,NativeBytecodeTest.selection(mod));
   check(r.has("error")&&r.get("error").getAsString().equals(expected),scenario+" rejected: "+r);
   check(r.get("dependency").getAsString().equals("irons_spellbooks")&&r.get("delivery").getAsString().equals("not_submitted"),"explicit dependency and no submission");
   check(DependencyProbe.keyInitializations==0&&PacketDistributor.sent.isEmpty(),"guard precedes all native mapping access and packets");
  }
  check(scenario.equals("missing")||scenario.equals("version")?ModList.fileReads==0:ModList.fileReads>0||scenario.equals("metadata"),"reject unavailable/wrong version before any JAR read");
  // Once native input already exists, rejection must preserve a player's held/pending pulse too.
  var busy=KeyMappings.QUICK_CAST_MAPPINGS.get(0);busy.m_7249_(true);busy.fixtureQueueClick();
  int presses=busy.presses,releases=busy.releases;
  var r=IronsNativeInput.submit("traveloptics","cast",NativeBytecodeTest.args("traveloptics"),NativeBytecodeTest.selection("traveloptics"));
  check(r.has("error")&&busy.presses==presses&&busy.releases==releases&&busy.m_90857_()&&busy.m_90859_()&&busy.consume()&&PacketDistributor.sent.isEmpty(),"failure preserves held, pending, queued native input");
  // Failure is not cached: repairing metadata/path admits the real native path in this same JVM.
  ModList.installed=true;ModList.fail=false;ModList.version="1.20.1-3.16.3";ModList.path=Path.of(argv[2]);
  busy.m_7249_(false);busy.consume();
  r=IronsNativeInput.submit("traveloptics","cast",NativeBytecodeTest.args("traveloptics"),NativeBytecodeTest.selection("traveloptics"));
  check(r.has("status")&&PacketDistributor.sent.size()==1,"failed check did not poison future verified invocation");
  System.out.println("NativeDependencyTest PASS scenario="+scenario+" assertions="+n+"; no native side effects on rejection");
 }
}'''
})
# Compile-time annotation seams avoid hiding missing-class warnings in the real bytecode harness.
SOURCES['net/minecraftforge/api/distmarker/Dist.java']='package net.minecraftforge.api.distmarker;public enum Dist {CLIENT,DEDICATED_SERVER}'
SOURCES['net/minecraftforge/fml/common/Mod.java']='package net.minecraftforge.fml.common;public @interface Mod {public @interface EventBusSubscriber {String modid() default "";net.minecraftforge.api.distmarker.Dist[] value() default {};Bus bus() default Bus.FORGE;public enum Bus {FORGE,MOD}}}'
for name,text in SOURCES.items():
 path=SRC/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(text,encoding='utf8')
with zipfile.ZipFile(IRON) as z:
 for name in ['player/ClientInputEvents','player/ExtendedKeyMapping','network/casting/CastPacket','network/casting/QuickCastPacket']:
  rel='io/redspace/ironsspellbooks/'+name+'.class';p=OUT/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(z.read(rel))
cache=Path('D:/Games/mc/mc-info-mcp/.gradle-user-home/caches')
gson=next(cache.rglob('gson-2.10.jar'))
cp=';'.join(map(str,[OUT,ROOT.parent.parent/'core/build/libs/core-0.1.2.jar',gson]))
argfile=WORK/'javac.args';argfile.write_text('\n'.join('"'+str(x).replace('\\','/')+'"' for x in ['-encoding','UTF-8','-classpath',cp,'-d',OUT,*[SRC/n for n in SOURCES]]),encoding='utf8')
# A same-version altered JAR must fail on the full-file hash, not its filename/manifest.
wrong_jar=WORK/'wrong-iron.jar';wrong_jar.write_bytes(IRON.read_bytes()+b'changed-runtime-fixture')
runtime_report={'dependency':{'mod_id':'irons_spellbooks','version':'1.20.1-3.16.3','sha256':TARGET['sha256']},'artifacts':[],'game_started':False}
def run_test(main,*args):
 proc=subprocess.run([str(JDK/'java.exe'),'-cp',cp,main,*map(str,args)],capture_output=True,text=True,encoding='utf8',errors='replace')
 print(proc.stdout,end='',flush=True);print(proc.stderr,end='',flush=True)
 proc.check_returncode()
 match=re.search(r'PASS .*assertions=(\d+)',proc.stdout)
 assert match is not None,f'missing test assertion count: {main}'
 return int(match[1])
for mod in ('irons_spellbooks','traveloptics'):
 # Load only this exact reobfuscated artifact, never a second optional provider JAR.
 shutil.rmtree(OUT/'dev/mcpmcp/magic',ignore_errors=True)
 artifact=ROOT/'build/libs'/f'{mod}-adapter.jar'
 with zipfile.ZipFile(artifact) as z:
  service=z.read('META-INF/services/dev.mcpmcp.api.McpmcpPlugin').decode().strip()
  assert service==f'dev.mcpmcp.magic.{"IronsProvider" if mod=="irons_spellbooks" else "TravelProvider"}'
  for name in z.namelist():
   if name.startswith('dev/mcpmcp/magic/') and name.endswith('.class'):
    p=OUT/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(z.read(name))
 subprocess.run([str(JDK/'javac.exe'),'@'+str(argfile)],check=True)
 result={'mod_id':mod,'sha256':hashlib.sha256(artifact.read_bytes()).hexdigest(),'cases':[]}
 for scenario,path in [('version',IRON),('hash',wrong_jar),('missing',IRON),('unreadable',WORK/'absent.jar'),('directory',WORK),('metadata',IRON)]:
  count=run_test('dev.mcpmcp.magic.NativeDependencyTest',scenario,path,IRON)
  result['cases'].append({'scenario':scenario,'assertions':count,'passed':True})
 result['native_bytecode_assertions']=run_test('dev.mcpmcp.magic.NativeBytecodeTest',IRON)
 runtime_report['artifacts'].append(result)
 print(f'Native runtime dependency verification PASS artifact={artifact.name} sha256={result["sha256"]}',flush=True)
(ROOT/'evidence/native-runtime-dependency.json').write_text(json.dumps(runtime_report,indent=2)+'\n',encoding='utf8')
