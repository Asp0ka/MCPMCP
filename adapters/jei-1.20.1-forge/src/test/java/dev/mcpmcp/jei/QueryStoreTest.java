package dev.mcpmcp.jei;
import com.google.gson.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/** Exercise pagination only through its bounded page/handle interface. */
final class QueryStoreTest {
    static void run() {
        AtomicLong clock=new AtomicLong(1000);QueryStore store=new QueryStore(clock::get);Object runtime=new Object();
        JsonObject query=new JsonObject();query.addProperty("query","match");
        QueryStore.Page first=store.page(runtime,1,"recipes",query,"",2,2,()->Stream.of(1,2,3,4,5),v->Integer.valueOf(3).equals(v)?row(v):null);
        AdapterTest.check(first.entries().isEmpty()&&!first.complete()&&first.scanned()==2,"empty bounded page has continuation");
        QueryStore.Page next=store.page(runtime,1,"recipes",query,first.next(),2,2,()->{throw new AssertionError("cursor must not restart scan");},v->Integer.valueOf(3).equals(v)?row(v):null);
        AdapterTest.check(next.entries().size()==1&&!next.complete()&&next.scanned()==2,"continuation progresses through unmatched entries");
        QueryStore.Page last=store.page(runtime,1,"recipes",query,next.next(),2,2,Stream::empty,QueryStoreTest::row);
        AdapterTest.check(last.complete()&&last.next()==null&&last.entries().size()==1,"last page reports completion");
        rejected(()->store.page(runtime,1,"recipes",query,last.next()==null?"unknown":last.next(),2,2,Stream::empty,QueryStoreTest::row));
        QueryStore.Page pinned=store.page(runtime,1,"recipes",query,"",1,1,()->Stream.of(1,2,3),QueryStoreTest::row);
        rejected(()->store.page(new Object(),1,"recipes",query,pinned.next(),1,1,Stream::empty,QueryStoreTest::row));
        rejected(()->store.page(runtime,2,"recipes",query,pinned.next(),1,1,Stream::empty,QueryStoreTest::row));
        JsonObject other=query.deepCopy();other.addProperty("query","different");rejected(()->store.page(runtime,1,"recipes",other,pinned.next(),1,1,Stream::empty,QueryStoreTest::row));
        clock.addAndGet(30001);rejected(()->store.page(runtime,1,"recipes",query,pinned.next(),1,1,Stream::empty,QueryStoreTest::row));
        String handle=store.remember(runtime,1,"category","recipe");
        AdapterTest.check(store.resolve(runtime,1,handle).recipe().equals("recipe"),"opaque handle resolves same recipe");
        rejected(()->store.resolve(new Object(),1,handle));rejected(()->store.resolve(runtime,2,handle));
        clock.addAndGet(120001);rejected(()->store.resolve(runtime,1,handle));
        String old=store.remember(runtime,1,"category",0);for(int i=0;i<512;i++)store.remember(runtime,1,"category",i);rejected(()->store.resolve(runtime,1,old));
        String live=store.remember(runtime,1,"category",1);store.clear();rejected(()->store.resolve(runtime,1,live));
        int[] closed={0};QueryStore.Page exp=store.page(runtime,1,"recipes",query,"",1,1,()->Stream.of(1,2).onClose(()->closed[0]++),QueryStoreTest::row);
        clock.addAndGet(30001);store.page(runtime,1,"other",query,"",1,1,Stream::empty,QueryStoreTest::row);
        AdapterTest.check(closed[0]==1,"expired iterator stream closed");store.clear();
    }
    static JsonObject row(Object n){JsonObject o=new JsonObject();o.addProperty("value",n.toString());return o;}
    interface Attempt {void run();}
    static void rejected(Attempt a){boolean ok=false;try{a.run();}catch(IllegalArgumentException e){ok=true;}AdapterTest.check(ok,"stale/mismatched state rejected");}
}
