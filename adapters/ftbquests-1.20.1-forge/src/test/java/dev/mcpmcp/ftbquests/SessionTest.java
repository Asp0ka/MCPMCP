package dev.mcpmcp.ftbquests;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import static dev.mcpmcp.ftbquests.Json.*;
import static dev.mcpmcp.ftbquests.AdapterTest.*;
final class SessionTest {
 static void run()throws Exception{
  long[] time={0};SessionIdentity s=new SessionIdentity(()->time[0]);Object[] refs={new Object(),new Object(),new Object(),new Object(),new Object()};String token=s.update(refs,"player/team","v1"),ctx=s.context();check(s.update(refs.clone(),"player/team","v1").equals(token),"stable same actual objects");check(!s.update(refs,"player/team","v2").equals(token)&&ctx.equals(s.context()),"progress update invalidates token retains pending context");token=s.update(refs,"player/team","v2");time[0]=30000;check(!s.update(refs,"player/team","v2").equals(token),"session TTL");
  for(int i=0;i<refs.length;i++){token=s.update(refs,"player/team","v2");ctx=s.context();refs[i]=new Object();check(!s.update(refs,"player/team","v2").equals(token)&&!ctx.equals(s.context()),"world/player/connection/file/team reference invalidates");}
  token=s.update(refs,"player/team","v2");check(!s.update(refs,"player/team2","v2").equals(token),"UUID-only team change invalidates");s.clear();check(!s.update(refs,"player/team2","v2").equals(token),"disconnect invalidates");
  Map<String,RuntimePin.Loaded> loaded=new HashMap<>();Map<Path,String> hashes=new HashMap<>();int[] digests={0};
  for(var p:RuntimePin.PINS){Path path=Path.of(p.modId()+"-selected.jar");loaded.put(p.modId(),new RuntimePin.Loaded(p.version(),path,new Object()));hashes.put(path,p.sha256());}
  RuntimePin pin=new RuntimePin(loaded::get,p->{digests[0]++;return hashes.get(p);});pin.verify();check(digests[0]==4,"all four exact runtime file pins");
  for(var p:RuntimePin.PINS){RuntimePin.Loaded original=loaded.remove(p.modId());rejects(pin::verify,"dependency_missing_"+p.modId());loaded.put(p.modId(),new RuntimePin.Loaded("wrong",original.path(),original.identity()));rejects(pin::verify,"version_mismatch_"+p.modId());loaded.put(p.modId(),original);hashes.put(original.path(),"wrong");rejects(pin::verify,"hash_mismatch_"+p.modId());hashes.put(original.path(),p.sha256());pin.verify();check(true,"failed pin not cached");}
  var p=RuntimePin.PINS.get(0);RuntimePin.Loaded original=loaded.get(p.modId());Path duplicate=Path.of("duplicate-identical-selected.jar");hashes.put(duplicate,p.sha256());loaded.put(p.modId(),new RuntimePin.Loaded(p.version(),duplicate,new Object()));pin.verify();check(true,"duplicate same bytes accepted based on ModList chosen file only");
  RuntimePin unreadable=new RuntimePin(loaded::get,path->{throw new IllegalStateException("read_unavailable");});rejects(unreadable::verify,"read_unavailable");
  JsonArray targets=JsonParser.parseString(Files.readString(Path.of(System.getProperty("adapter.projectDir"),"targets.json"))).getAsJsonArray();for(JsonElement e:targets){JsonObject t=e.getAsJsonObject();check(RuntimePin.digest(Path.of(str(t,"path",""))).equals(str(t,"sha256","")),"actual target jar full hash");}
 }
}
