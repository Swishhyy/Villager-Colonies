package io.github.swishhyy.wwmc.core;

/** Inclusive coordinates; independent of Minecraft and safe to snapshot. */
public record RoomBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public RoomBounds {
        if (minX > maxX || minY > maxY || minZ > maxZ) throw new IllegalArgumentException("Normalize room corners");
    }
    public static RoomBounds between(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new RoomBounds(Math.min(x1,x2), Math.min(y1,y2), Math.min(z1,z2), Math.max(x1,x2), Math.max(y1,y2), Math.max(z1,z2));
    }
    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }
    public boolean intersects(RoomBounds o) {
        return minX <= o.maxX && maxX >= o.minX && minY <= o.maxY && maxY >= o.minY && minZ <= o.maxZ && maxZ >= o.minZ;
    }
    public boolean withinLimit(int limit) {
        long x = (long) maxX - minX + 1, y = (long) maxY - minY + 1, z = (long) maxZ - minZ + 1;
        return limit > 0 && x <= limit && y <= limit && z <= limit && x*y*z <= limit;
    }
}
