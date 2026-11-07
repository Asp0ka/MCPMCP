package dev.mcpmcp.curios;
import java.util.*;
import static dev.mcpmcp.curios.AdapterTest.check;
/** No Minecraft host substitution: token invalidation uses the production identity algorithm. */
final class SessionTest {
 static void run(){
  long[] now={0};SessionIdentity s=new SessionIdentity(()->now[0]);Object world=new Object(),player=new Object(),connection=new Object(),menu=new Object(),screen=new Object(),handler=new Object(),slot=new Object();
  String original=s.update(world,player,connection,menu,screen,handler,List.of(slot),"page0");
  check(original.equals(s.update(world,player,connection,menu,screen,handler,List.of(slot),"page0")),"same actual identities retain token");
  String page=s.update(world,player,connection,menu,screen,handler,List.of(slot),"page1");check(!page.equals(original),"page/grid/view change expires token");
  String rebuilt=s.update(world,player,connection,menu,screen,handler,List.of(new Object()),"page1");check(!rebuilt.equals(page),"same numeric page with rebuilt slots expires token");
  String before=s.update(world,player,connection,menu,screen,handler,List.of(slot),"page1");now[0]=30000;String after=s.update(world,player,connection,menu,screen,handler,List.of(slot),"page1");check(!before.equals(after),"30 second token TTL");
  String instance=s.instance();s.update(new Object(),player,connection,menu,screen,handler,List.of(slot),"page1");check(!instance.equals(s.instance()),"world object bound even with reused menu");
  instance=s.instance();s.update(world,player,new Object(),menu,screen,handler,List.of(slot),"page1");check(!instance.equals(s.instance()),"connection object bound");
  before=s.update(world,player,connection,menu,screen,handler,List.of(slot),"page1");after=s.update(world,player,connection,menu,new Object(),handler,List.of(slot),"page1");check(!before.equals(after),"screen replaced invalidates token");
  before=s.update(world,player,connection,menu,null,handler,List.of(slot),"closed");check(!before.equals(after),"closing screen invalidates token");
 }
}
