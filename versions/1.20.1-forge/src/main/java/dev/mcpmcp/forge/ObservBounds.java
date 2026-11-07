package dev.mcpmcp.forge;

/** Overflow-safe inclusive box traversal. Candidate budget applies even to unloaded/filtered positions. */
public final class ObservBounds {
    public static int budget(int configured) { return Math.max(1, Math.min(4096, configured)); }
    public static int pageSize(int requested, int configured) {
        if (requested < 1) throw new IllegalArgumentException("positive_limit_required");
        return Math.min(requested, budget(configured));
    }
    public static long volume(int x0,int y0,int z0,int x1,int y1,int z1) {
        if(x1<x0||y1<y0||z1<z0)throw new IllegalArgumentException("reversed_bounds");
        if(x0 < -30000000 || x1 > 30000000 || z0 < -30000000 || z1 > 30000000 || y0 < -2048 || y1 > 2047)
            throw new IllegalArgumentException("outside_world_bounds");
        return Math.multiplyExact(Math.multiplyExact((long)x1-x0+1,(long)y1-y0+1),(long)z1-z0+1);
    }
    public static int[] position(int x0,int y0,int z0,int x1,int y1,int z1,long offset) {
        long volume=volume(x0,y0,z0,x1,y1,z1);
        if(offset<0||offset>=volume)throw new IllegalArgumentException("invalid_offset");
        long nx=(long)x1-x0+1, ny=(long)y1-y0+1;
        return new int[]{(int)(x0+offset%nx),(int)(y0+(offset/nx)%ny),(int)(z0+offset/nx/ny)};
    }
    private ObservBounds() {}
}
