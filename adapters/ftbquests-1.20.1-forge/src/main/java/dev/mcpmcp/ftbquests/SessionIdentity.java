package dev.mcpmcp.ftbquests;
import java.util.*;
import java.util.function.LongSupplier;
/** Session identity uses actual references plus UUID and full captured content version. */
final class SessionIdentity {
 private final LongSupplier clock;private Object[] refs=new Object[0];private String identity="",version="",session="",context="";private long issued;
 SessionIdentity(){this(System::currentTimeMillis);}
 SessionIdentity(LongSupplier clock){this.clock=clock;}
 String update(Object[] next,String uuidIdentity,String version){
  boolean same=refs.length==next.length;for(int i=0;same&&i<next.length;i++)same=refs[i]==next[i];
  long now=clock.getAsLong();
  boolean changed=!same||!identity.equals(uuidIdentity);
  if(changed){context=UUID.randomUUID().toString();refs=next.clone();identity=uuidIdentity;}
  if(changed||!this.version.equals(version)||now-issued>=30000||session.isEmpty()){session=UUID.randomUUID().toString();issued=now;}
  this.version=version;return session;
 }
 String context(){return context;}
 void clear(){refs=new Object[0];identity="";version="";session="";context="";}
}
