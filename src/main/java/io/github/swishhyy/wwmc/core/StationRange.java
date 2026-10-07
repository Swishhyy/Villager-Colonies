package io.github.swishhyy.wwmc.core;

/** The base station range, shared by server detection and the client preview; range upgrades add to it (see {@link Upgrades}). */
public final class StationRange {
    public static final int RADIUS = 3;
    public static final int SIZE = RADIUS * 2 + 1;
    private StationRange() {}
    public static RoomBounds around(int x, int y, int z) { return around(x,y,z,RADIUS); }
    public static RoomBounds around(int x, int y, int z, int radius) {
        return new RoomBounds(x-radius, y-radius, z-radius, x+radius, y+radius, z+radius);
    }
}
