package dev.mcpmcp.forge;
import com.google.gson.*;
import dev.mcpmcp.core.*;
import dev.mcpmcp.api.*;
import net.minecraftforge.fml.ModList;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.security.MessageDigest;
import java.net.*;
/** Declarative extensions hot-swap atomically;
Java providers load once from locally trusted code. */
public final class PluginManager {
private final Path root;
private final PluginFiles files;
private PluginContext context;
private String revision="";
private JsonArray plugins=new JsonArray();
private Map<String,JsonObject> declarations=Map.of();
private final Map<String,BinaryAction> binaryActions=new LinkedHashMap<>();
private final Map<String,List<Observer>> observers=new LinkedHashMap<>();
private final List<URLClassLoader> loaders=new ArrayList<>();
private String error;
private boolean javaAttempted;
private record BinaryAction(JsonObject schema,String transport,String permission,PluginContext.Handler handler){
}
private record Observer(int priority,PluginContext.Handler handler){
}
public PluginManager(Path p){
root=p;
files=new PluginFiles(p);
}
public void setContext(PluginContext c){
context=c;
}
public synchronized JsonObject reload(){
try{
Map<String,JsonObject> next=new LinkedHashMap<>();
JsonArray summaries=new JsonArray();
MessageDigest digest=MessageDigest.getInstance("SHA-256");
Set<String> ids=new HashSet<>();
List<Path> dirs;
try(var stream=Files.list(root)){
dirs=stream.filter(p->Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS)&&!Files.isSymbolicLink(p)).sorted().limit(257).toList();
}
if(dirs.size()>256)throw new IllegalArgumentException("plugin_count_limit");
for(Path dir:dirs){
String folder=dir.getFileName().toString();
Path manifest=files.resolve(folder+"/plugin.json");
if(!Files.exists(manifest))continue;
if(Files.size(manifest)>262144)throw new IllegalArgumentException("manifest_too_large");
byte[] bytes=Files.readAllBytes(manifest);
digest.update(folder.getBytes(java.nio.charset.StandardCharsets.UTF_8));
digest.update((byte)0);
digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());
digest.update(bytes);
JsonObject m=JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
PluginValidation.manifest(m);
if(Jsons.integer(m,"api",0)!=1)throw new IllegalArgumentException("plugin_api_mismatch");
String id=Jsons.str(m,"id","");
if(!id.matches("[a-z0-9_.-]{1,96}"))throw new IllegalArgumentException("invalid_plugin_id");
if(!ids.add(id))throw new IllegalArgumentException("duplicate_plugin_id");
String mod=Jsons.str(m,"mod_id","");
boolean installed=mod.equals("minecraft")||ModList.get().isLoaded(mod);
String status=installed?"active":"target_mod_missing";
String expected=Jsons.str(m,"target_sha256","");
String actual="";
if(installed&&!mod.equals("minecraft")){
var info=ModList.get().getModFileById(mod);
Path jar=info.getFile().getFilePath();
if(Files.isRegularFile(jar)){
actual=hash(jar);
if(!expected.matches("[a-f0-9]{64}")||!expected.equals(actual))status="target_hash_mismatch";
}
else status="target_jar_unavailable";
}
if(!Jsons.str(m,"minecraft","1.20.1").equals("1.20.1")||!Jsons.str(m,"loader","forge").equals("forge"))status="platform_mismatch";
JsonArray actions=Jsons.array(m,"actions");
Path external=files.resolve(folder+"/actions.json");
if(Files.exists(external)){
if(Files.isSymbolicLink(external)||Files.size(external)>262144)throw new IllegalArgumentException("invalid_actions_file");
byte[] ab=Files.readAllBytes(external);
digest.update(java.nio.ByteBuffer.allocate(4).putInt(ab.length).array());
digest.update(ab);
actions=JsonParser.parseString(new String(ab,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();
}
PluginValidation.actions(actions);
Set<String> localNames=new HashSet<>();
for(JsonElement e:actions){
JsonObject a=e.getAsJsonObject();
String n=Jsons.str(a,"name","");
if(!localNames.add(n)||Runtime.isBuiltin(n)||binaryActions.containsKey(n))throw new IllegalArgumentException("plugin_action_name_conflict");
if(status.equals("active"))preflightPacket(a.getAsJsonObject("packet"));
}
if(status.equals("active"))for(JsonElement a:actions){
JsonObject action=a.getAsJsonObject().deepCopy();
String name=Jsons.str(action,"name","");
if(!name.matches("[a-z0-9_.-]{1,128}")||next.containsKey(name)||Runtime.isBuiltin(name))throw new IllegalArgumentException("plugin_action_name_conflict");
if(!Set.of("normal","hack","cheat").contains(Jsons.str(action,"permission","normal")))throw new IllegalArgumentException("invalid_plugin_permission");
if(!Jsons.str(action,"transport","packet").equals("packet"))throw new IllegalArgumentException("declarative_actions_require_packet_transport");
action.addProperty("plugin_id",id);
next.put(name,action);
}
summaries.add(Jsons.obj("id",id,"folder",dir.getFileName().toString(),"mod_id",mod,"status",status,"target_sha256",actual,"declarations_hot_reload",true,"java_present",Files.exists(files.resolve(folder+"/adapter.jar")),"java_reload","restart_required"));
}
revision=HexFormat.of().formatHex(digest.digest());
declarations=Map.copyOf(next);
plugins=summaries;
error=null;
return Jsons.obj("observed_success",true,"revision",revision,"plugins",plugins,"actions",next.size());
}
catch(Exception|LinkageError e){
error=e.getClass().getSimpleName()+": "+Objects.toString(e.getMessage(),"");
return Jsons.obj("error","plugin_reload_failed","reason",error,"retained_revision",revision);
}
}
public synchronized void loadTrustedJavaOnce(){
if(javaAttempted)return;
javaAttempted=true;
if(!Runtime.config().trustJavaPlugins||context==null)return;
for(JsonElement e:plugins){
JsonObject p=e.getAsJsonObject();
if(!Jsons.str(p,"status","").equals("active"))continue;
try{
Path jar=files.resolve(Jsons.str(p,"folder","")+"/adapter.jar");
if(!Files.isRegularFile(jar,LinkOption.NOFOLLOW_LINKS))continue;
URLClassLoader loader=new URLClassLoader(new URL[]{
jar.toUri().toURL()}
,McpmcpPlugin.class.getClassLoader());
loaders.add(loader);
Iterator<McpmcpPlugin> providers=ServiceLoader.load(McpmcpPlugin.class,loader).iterator();
for(int attempts=0;
attempts<256;
attempts++){
McpmcpPlugin plugin;
try{
if(!providers.hasNext())break;
plugin=providers.next();
}
catch(ServiceConfigurationError|LinkageError ex){
javaError(p,ex);
continue;
}
try{
RestrictedContext staged=new RestrictedContext(plugin.id());
plugin.register(staged);
staged.commit();
}
catch(Exception|ServiceConfigurationError|LinkageError ex){
javaError(p,ex);
}
}
}
catch(Exception|ServiceConfigurationError|LinkageError ex){
javaError(p,ex);
}
}
}
private static void javaError(JsonObject p,Throwable ex){
String prior=Jsons.str(p,"java_error","");
p.addProperty("java_error",(prior+"; "+ex).substring(0,Math.min(4096,(prior+"; "+ex).length())));
}
private final class RestrictedContext implements PluginContext {
final String owner;
final Map<String,BinaryAction> stagedActions=new LinkedHashMap<>();
final Map<String,List<Observer>> stagedObservers=new LinkedHashMap<>();
boolean closed;
RestrictedContext(String owner){
if(owner==null||!owner.matches("[a-z0-9_.-]{1,96}"))throw new IllegalArgumentException("invalid_plugin_id");
this.owner=owner;
}
private void open(){
if(closed)throw new IllegalStateException("registration_closed");
}
public void action(String n,JsonObject s,String t,String p,Handler h){
open();
if(n==null||!n.matches("[a-z0-9_.-]{1,128}")||Runtime.isBuiltin(n)||declarations.containsKey(n)||binaryActions.containsKey(n)||stagedActions.containsKey(n))throw new IllegalArgumentException("action_conflict");
if(!Set.of("normal","hack","cheat").contains(p)||!Set.of("packet","client","input","baritone").contains(t)||h==null)throw new IllegalArgumentException("invalid_action_policy");
PluginValidation.schema(s);
stagedActions.put(n,new BinaryAction(s.deepCopy(),t,p,h));
}
public void observer(String k,int priority,Handler h){
open();
if(k==null||!k.matches("[a-z0-9_.-]{1,128}")||h==null)throw new IllegalArgumentException("invalid_observer");
stagedObservers.computeIfAbsent(k,x->new ArrayList<>()).add(new Observer(priority,h));
}
void commit(){
open();
closed=true;
binaryActions.putAll(stagedActions);
stagedObservers.forEach((k,v)->{
observers.computeIfAbsent(k,x->new ArrayList<>()).addAll(v);
observers.get(k).sort(Comparator.comparingInt(Observer::priority).reversed());
}
);
}
public JsonObject execute(String n,JsonObject a){
return context.execute(n,a);
}
public JsonObject observe(String k,JsonObject a){
return context.observe(k,a);
}
}
public synchronized JsonObject status(){
return Jsons.obj("plugins",plugins,"revision",revision,"error",error,"java_loaded",loaders.size(),"java_trusted",Runtime.config().trustJavaPlugins,"file_policy","plugin_text_only; no external client file mutation");
}
public synchronized boolean hasAction(String n){
return declarations.containsKey(n)||binaryActions.containsKey(n);
}
public synchronized JsonObject describe(String n){
if(declarations.containsKey(n))return declarations.get(n).deepCopy();
BinaryAction b=binaryActions.get(n);
return b==null?Jsons.error("unknown_plugin_action"):Jsons.obj("name",n,"input_schema",b.schema,"transport",b.transport,"permission",b.permission,"java",true);
}
public synchronized JsonObject actions(){
JsonArray all=new JsonArray();
declarations.values().forEach(a->all.add(a.deepCopy()));
binaryActions.keySet().forEach(n->all.add(describe(n)));
return Jsons.obj("entries",all,"revision",revision);
}
public synchronized String revision(){
return revision;
}
public JsonObject parameters(JsonObject args){
JsonObject p=args.deepCopy();
p.remove("plugin_revision");
return p;
}
public synchronized Jobs.Step create(String n,JsonObject args){
final String pinned=revision;
final JsonObject declaration=describe(n),parameters=parameters(args);
final BinaryAction binary=binaryActions.get(n);
final JsonArray verify=new JsonArray();
final boolean wantsVerify=declaration.has("verify");
return new PluginStep(()->{
if(declaration.has("error"))return declaration;
if(!pinned.equals(revision)||(args.has("plugin_revision")&&!Jsons.str(args,"plugin_revision","").equals(pinned)))return Jsons.error("plugin_revision_changed");
String transport=Jsons.str(declaration,"transport","packet"),permission=Jsons.str(declaration,"permission","normal");
if(!Policy.allowed(Runtime.config().mode,Runtime.config().transport,transport,permission))return Jsons.error("plugin_policy_denied");
Schema.validate(parameters,Jsons.object(declaration,"input_schema"));
if(binary!=null)return binary.handler.call(parameters);
for(JsonElement c:Jsons.array(declaration,"preconditions")){
JsonObject bound=bind(c,parameters).getAsJsonObject();
PluginValidation.condition(bound,Jsons.object(declaration,"input_schema"),false);
if(!Actions.condition(bound))return Jsons.error("plugin_precondition_failed");
}
for(JsonElement c:Jsons.array(declaration,"verify")){
JsonObject bound=bind(c,parameters).getAsJsonObject();
PluginValidation.condition(bound,Jsons.object(declaration,"input_schema"),false);
verify.add(bound);
}
return PacketService.send(bind(declaration.get("packet"),parameters).getAsJsonObject());
}
,wantsVerify?()->{
if(!pinned.equals(revision))throw new IllegalStateException("plugin_revision_changed");
for(JsonElement c:verify)if(!Actions.condition(c.getAsJsonObject()))return false;
return true;
}
:null);
}
/** Compatibility for one tick only;
callers needing verify must retain create(). */
public JsonObject execute(String n,JsonObject a){
try{
return create(n,a).tick();
}
catch(Exception ex){
return Jsons.obj("error","plugin_action_failed","reason",ex.toString());
}
}
private static void preflightPacket(JsonObject packet)throws Exception{
String name=Jsons.str(packet,"packet_class","");
JsonObject metadata=null;
for(JsonElement e:Jsons.array(PacketService.read("packets",Jsons.obj("query",name,"direction","C2S","limit",500)),"entries")){
JsonObject row=e.getAsJsonObject();
if(name.equals(Jsons.str(row,"class",""))){
metadata=row;
break;
}
}
if(metadata==null)throw new IllegalArgumentException("packet_not_discovered_c2s");
if(metadata.has("error")||!Jsons.str(metadata,"direction","").equals("C2S"))throw new IllegalArgumentException("packet_not_discovered_c2s");
if(packet.has("channel")&&!Jsons.str(packet,"channel","").equals(Jsons.str(metadata,"channel","")))throw new IllegalArgumentException("packet_channel_mismatch");
if(Jsons.str(packet,"mode","constructor").equals("wire")){
if(!Jsons.str(metadata,"id","").startsWith("vanilla:"))throw new IllegalArgumentException("wire_requires_vanilla");
return;
}
JsonArray signature=Jsons.array(packet,"signature");
boolean found=false;
Class<?> type=Class.forName(name,false,PluginManager.class.getClassLoader());
for(var ctor:type.getConstructors()){
Class<?>[] ts=ctor.getParameterTypes();
if(ts.length!=signature.size())continue;
boolean match=true;
for(int i=0;
i<ts.length;
i++)match&=ts[i].getName().equals(signature.get(i).getAsString());
if(match){
found=true;
for(int i=0;
i<ts.length;
i++){
JsonElement v=Jsons.array(packet,"values").get(i);
if(ts[i].isEnum()&&v.isJsonPrimitive()){
boolean enumFound=false;
for(var f:ts[i].getDeclaredFields())enumFound|=f.isEnumConstant()&&f.getName().equals(v.getAsString());
if(!enumFound)throw new IllegalArgumentException("unknown_enum_constant");
}
}
}
if(match)for(Class<?> t:ts)if(!t.isEnum()&&!Set.of("java.lang.String","int","java.lang.Integer","long","java.lang.Long","short","byte","boolean","float","double","java.util.UUID","net.minecraft.resources.ResourceLocation","net.minecraft.core.BlockPos","net.minecraft.nbt.CompoundTag","net.minecraft.world.item.ItemStack").contains(t.getName()))throw new IllegalArgumentException("unsupported_constructor_type_"+t.getName());
}
if(!found)throw new IllegalArgumentException("constructor_not_found");
}
public Optional<JsonObject> observe(String kind,JsonObject args){
List<Observer> o=observers.get(kind);
if(o==null)return Optional.empty();
for(Observer observer:o)try{
JsonObject r=observer.handler.call(args.deepCopy());
if(r!=null&&!r.has("error"))return Optional.of(r);
}
catch(Exception ignored){
}
return Optional.empty();
}
public JsonObject readFile(String path){
return Jsons.obj("path",path,"text",files.read(path));
}
public JsonObject writeFile(String path,String text){
files.write(path,text);
return Jsons.obj("observed_success",true,"path",path,"reload_required",true);
}
private static JsonElement bind(JsonElement value,JsonObject args){
if(value==null)throw new IllegalArgumentException("missing_packet_declaration");
if(value.isJsonObject()){
JsonObject o=value.getAsJsonObject();
if(o.size()==1&&o.has("param")){
String key=o.get("param").getAsString();
if(!args.has(key))throw new IllegalArgumentException("missing_parameter_"+key);
return args.get(key).deepCopy();
}
if(o.size()==1&&o.has("context")){
MinecraftContext c=new MinecraftContext();
return c.get(o.get("context").getAsString());
}
JsonObject r=new JsonObject();
o.entrySet().forEach(e->r.add(e.getKey(),bind(e.getValue(),args)));
return r;
}
if(value.isJsonArray()){
JsonArray r=new JsonArray();
value.getAsJsonArray().forEach(v->r.add(bind(v,args)));
return r;
}
return value.deepCopy();
}
private static class MinecraftContext {
JsonElement get(String key){
var mc=net.minecraft.client.Minecraft.getInstance();
return switch(key){
case "menu_id"->new JsonPrimitive(mc.player.containerMenu.containerId);
case "menu_state_id"->new JsonPrimitive(mc.player.containerMenu.getStateId());
case "player_uuid"->new JsonPrimitive(mc.player.getUUID().toString());
default->throw new IllegalArgumentException("unknown_context_key");
}
;
}
}
private static String hash(Path p)throws Exception{
MessageDigest digest=MessageDigest.getInstance("SHA-256");
try(var stream=Files.newInputStream(p)){
byte[] b=new byte[65536];
int n;
while((n=stream.read(b))!=-1)digest.update(b,0,n);
}
return HexFormat.of().formatHex(digest.digest());
}
}
