package dev.mcpmcp.core;
public final class SessionLease {
 private final int original;private final boolean wasOpen;private Integer owned;
 public SessionLease(int original,boolean wasOpen){this.original=original;this.wasOpen=wasOpen;}
 public void opened(int id){if(wasOpen||id==0)throw new IllegalArgumentException("invalid_session_ownership");owned=id;}
 public String observe(int current,boolean open){if(wasOpen)return current==original&&open?"operate":"conflict";if(owned!=null)return current==owned&&open?"operate":"conflict";return current==0&&!open?"open":"conflict";}
 public String finish(int current){return !wasOpen&&owned!=null&&current==owned?"close":"keep";}
}
