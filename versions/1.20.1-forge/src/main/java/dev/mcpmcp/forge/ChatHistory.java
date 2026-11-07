package dev.mcpmcp.forge;

import com.google.gson.*;
import java.util.*;

/** Bounded, sequence-addressed received-message memory; never persists chat to disk. */
public final class ChatHistory {
    private record Entry(long sequence, long time, String kind, String text, boolean truncated) {}
    private final int capacity, maxChars;
    private final ArrayDeque<Entry> entries=new ArrayDeque<>();
    private long sequence;
    public ChatHistory(int capacity,int maxChars) {
        if(capacity<1||capacity>4096||maxChars<1||maxChars>8192)throw new IllegalArgumentException("invalid_chat_bounds");
        this.capacity=capacity;this.maxChars=maxChars;
    }
    public synchronized void add(String text,String kind,long time) {
        Objects.requireNonNull(text);Objects.requireNonNull(kind);
        boolean truncated=text.length()>maxChars;
        int end=Math.min(maxChars,text.length());
        if(end>0&&end<text.length()&&Character.isHighSurrogate(text.charAt(end-1)))end--;
        entries.addLast(new Entry(++sequence,time,kind,text.substring(0,end),truncated));
        while(entries.size()>capacity)entries.removeFirst();
    }
    public synchronized JsonObject read(long after,int limit) {
        if(after<0||limit<1)throw new IllegalArgumentException("invalid_chat_page");
        limit=Math.min(limit,Math.min(capacity,4096));
        JsonArray values=new JsonArray();long next=after;
        boolean more=false;
        for(Entry e:entries)if(e.sequence>after) {
            if(values.size()==limit){more=true;break;}
            JsonObject v=new JsonObject();v.addProperty("sequence",e.sequence);v.addProperty("observed_at",e.time);
            v.addProperty("kind",e.kind);v.addProperty("text",e.text);v.addProperty("truncated",e.truncated);values.add(v);next=e.sequence;
        }
        JsonObject result=new JsonObject();result.add("entries",values);result.addProperty("next_after",next);
        result.addProperty("more",more);result.addProperty("capacity",capacity);
        result.addProperty("dropped_before",entries.isEmpty()?sequence:entries.getFirst().sequence-1);
        result.addProperty("latest_sequence",sequence);return result;
    }
}
