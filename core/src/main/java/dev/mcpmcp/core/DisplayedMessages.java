package dev.mcpmcp.core;
import java.util.*;
/** Bounded identity cursor for a newest-first native display list. */
public final class DisplayedMessages {
 private Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());private final int capacity;private Object top;
 public DisplayedMessages(int capacity){if(capacity<1||capacity>512)throw new IllegalArgumentException("invalid_display_capacity");this.capacity=capacity;}
 public <T> List<T> take(List<T> newestFirst){
  if(newestFirst.isEmpty()){seen.clear();top=null;return List.of();}
  if(newestFirst.get(0)==top)return List.of();
  int end=Math.min(capacity,newestFirst.size());List<T> fresh=new ArrayList<>();
  Set<Object> current=Collections.newSetFromMap(new IdentityHashMap<>());
  for(int i=0;i<end;i++){T message=newestFirst.get(i);current.add(message);if(!seen.contains(message))fresh.add(message);}
  seen=current;top=newestFirst.get(0);Collections.reverse(fresh);return fresh;
 }
}
