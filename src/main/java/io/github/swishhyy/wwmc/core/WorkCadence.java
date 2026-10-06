package io.github.swishhyy.wwmc.core;

import java.util.function.BooleanSupplier;

/** Keep a citizen's update rate while spreading a recruited group over different server ticks. */
public final class WorkCadence {
    public static final int REACH_CHECKS=4;
    private WorkCadence() {}
    public static boolean due(long tick,int citizen,int interval) {
        return Math.floorMod(tick,interval)==Math.floorMod(citizen,interval);
    }
    /** Exhausting this allowance defers a search; it does not declare a work site unreachable. */
    public static final class ReachBudget {
        private int used;
        private boolean deferred;
        public void reset() { used=0; deferred=false; }
        public boolean deferred() { return deferred; }
        public boolean check(BooleanSupplier reachable) {
            if(used>=REACH_CHECKS) { deferred=true; return false; }
            used++; return reachable.getAsBoolean();
        }
    }
}
