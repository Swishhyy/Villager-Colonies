package io.github.swishhyy.wwmc.core;

public final class ShiftClock {
    private ShiftClock() {}
    public static boolean night(long ticks) {
        long time=Math.floorMod(ticks,24000L);
        return time>=13000 && time<23000;
    }
}
