package dev.mcpmcp.curios;

import java.util.*;
import java.util.function.LongSupplier;

/** Identity, not menu IDs or identityHashCode. Slot rebuilds invalidate the old token. */
final class SessionIdentity {
 private final LongSupplier clock;
 private List<Object> identities=List.of();private String layout="",token="",instance="";
 private Object menu,world,player,connection;private long issued;
 SessionIdentity(){this(System::currentTimeMillis);}
 SessionIdentity(LongSupplier clock){this.clock=clock;}
 String update(Object world,Object player,Object connection,Object menu,Object screen,Object handler,List<?> slots,String layout){
  if(this.menu!=menu||this.world!=world||this.player!=player||this.connection!=connection){instance=UUID.randomUUID().toString();this.menu=menu;this.world=world;this.player=player;this.connection=connection;}
  List<Object> next=new ArrayList<>(List.of(world,player,connection,menu));next.add(screen);next.add(handler);next.addAll(slots);
  boolean same=identities.size()==next.size();if(same)for(int i=0;i<next.size();i++)if(identities.get(i)!=next.get(i)){same=false;break;}
  long now=clock.getAsLong();if(!same||!this.layout.equals(layout)||now-issued>=30000){identities=next;this.layout=layout;issued=now;token=UUID.randomUUID().toString();}
  return token;
 }
 String instance(){return instance;}
 void clear(){identities=List.of();token="";instance="";menu=null;world=null;player=null;connection=null;}
}
