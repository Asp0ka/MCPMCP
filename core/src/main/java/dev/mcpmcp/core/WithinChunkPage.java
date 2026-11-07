package dev.mcpmcp.core;
/** Cursor for a budgeted inner list without skipping its enclosing chunk. */
public final class WithinChunkPage {
 public record Next(long outer,int inner){}
 public static Next next(long outer,int inner,int total,int consumed,int outerTotal){if(outer<0||outer>=outerTotal||inner<0||consumed<0||inner+consumed>total)throw new IllegalArgumentException("invalid_inner_page");return inner+consumed<total?new Next(outer,inner+consumed):new Next(outer+1,0);}
 private WithinChunkPage(){}
}
