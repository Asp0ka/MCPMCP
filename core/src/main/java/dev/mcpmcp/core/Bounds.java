package dev.mcpmcp.core;
import com.google.gson.*;
public record Bounds(int minX,int minY,int minZ,int maxX,int maxY,int maxZ) {
 public static Bounds of(JsonObject a,long limit){Bounds b=new Bounds(Jsons.integer(a,"min_x",0),Jsons.integer(a,"min_y",-64),Jsons.integer(a,"min_z",0),Jsons.integer(a,"max_x",0),Jsons.integer(a,"max_y",320),Jsons.integer(a,"max_z",0));
  if(b.minX>b.maxX||b.minY>b.maxY||b.minZ>b.maxZ||Math.abs((long)b.minX)>30000000||Math.abs((long)b.maxX)>30000000||Math.abs((long)b.minZ)>30000000||Math.abs((long)b.maxZ)>30000000||b.minY < -2048||b.maxY>2048||b.volume()>limit)throw new IllegalArgumentException("invalid_or_oversized_region");return b;}
 public long volume(){try{return Math.multiplyExact(Math.multiplyExact((long)maxX-minX+1,(long)maxY-minY+1),(long)maxZ-minZ+1);}catch(ArithmeticException e){return Long.MAX_VALUE;}}
 public int[] position(long i){if(i<0||i>=volume())throw new IllegalArgumentException("invalid_offset");long dx=(long)maxX-minX+1,dz=(long)maxZ-minZ+1;return new int[]{minX+(int)(i%dx),minY+(int)(i/(dx*dz)),minZ+(int)((i/dx)%dz)};}
}
