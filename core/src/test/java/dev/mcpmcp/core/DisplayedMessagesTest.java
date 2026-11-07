package dev.mcpmcp.core;
import java.util.*;
public final class DisplayedMessagesTest {
 private static int checks;
 private static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;}
 public static void main(String[] args){
  DisplayedMessages tracker=new DisplayedMessages(3);
  Object first=new String("same"),second=new String("same"),third=new Object(),fourth=new Object();
  check(tracker.take(List.of(second,first)).equals(List.of(first,second)),"oldest first");
  check(tracker.take(List.of(second,first)).isEmpty(),"no repeated reads");
  check(tracker.take(List.of(third,second,first)).equals(List.of(third)),"same text different message identities preserved");
  check(tracker.take(List.of(fourth,third,second)).equals(List.of(fourth)),"rolling visible list");
  check(tracker.take(List.of()).isEmpty(),"clear empty");
  check(tracker.take(List.of(first)).equals(List.of(first)),"new display after clear");
  check(tracker.take(List.of(fourth,third,second,first)).size()==3,"bounded scanning");
  System.out.println("DisplayedMessagesTest: "+checks+" checks passed");
 }
}
