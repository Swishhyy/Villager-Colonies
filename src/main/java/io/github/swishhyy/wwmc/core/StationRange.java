package io.github.swishhyy.wwmc.core;

/** One shared range for server detection and the client preview. */
public final class StationRange {
    public static final int RADIUS = 3;
    public static final int SIZE = RADIUS * 2 + 1;
    private StationRange() {}
    public static RoomBounds around(int x, int y, int z) {
        return new RoomBounds(x-RADIUS, y-RADIUS, z-RADIUS, x+RADIUS, y+RADIUS, z+RADIUS);
    }
}
