package dev.mcpmcp.forge;

import com.google.gson.JsonObject;
import java.util.*;
import static dev.mcpmcp.core.Jsons.*;

/** Real cursor registry tests; no Minecraft initialization. */
public final class ObservPagesTest {
    private static int checks;
    private static void check(boolean v,String name){if(!v)throw new AssertionError(name);checks++;}
    public static void main(String[] ignored){
        Object a=new Object(),b=new Object();JsonObject query=obj("query","ore","limit",1);
        var first=ObservPages.take("entities",a,query,1,()->List.of(1,2,3).iterator());
        check(first.candidates().equals(List.of(1)),"first page");
        ObservPages.take("recipes",b,new JsonObject(),1,()->List.of(7,8).iterator());
        JsonObject next=query.deepCopy();next.addProperty("cursor",first.next());
        var second=ObservPages.<Integer>take("entities",a,next,1,()->{throw new AssertionError("cursor recreated");});
        check(second.candidates().equals(List.of(2)),"other observation must not evict cursor");
        next.addProperty("query","different");
        try{ObservPages.take("entities",a,next,1,Collections::emptyIterator);throw new AssertionError("mismatched query accepted");}catch(IllegalArgumentException expected){checks++;}
        next=query.deepCopy();next.addProperty("cursor",first.next());
        try{ObservPages.take("entities",new Object(),next,1,Collections::emptyIterator);throw new AssertionError("other world accepted");}catch(IllegalArgumentException expected){checks++;}
        System.out.println("ObservPagesTest: "+checks+" checks passed");
    }
}
