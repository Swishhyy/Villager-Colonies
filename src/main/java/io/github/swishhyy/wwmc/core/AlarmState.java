package io.github.swishhyy.wwmc.core;

/** A town's alarm cycle: calm, a guard running to the bell, then the ringing alarm until a quiet all-clear. */
public final class AlarmState {
    public enum Phase { CALM, RAISING, ALARM }
    public enum Signal { NONE, DISPATCH, STAND_DOWN, ALL_CLEAR }
    /** Quiet time without a sighted hostile before the all-clear. */
    public static final int ALL_CLEAR_TICKS=600;
    /** Time a runner has to reach the bell before another guard is sent. */
    public static final int RUN_TICKS=1200;
    private Phase phase=Phase.CALM;
    private int quietTicks;
    private long deadline;
    public Phase phase() { return phase; }
    public boolean ringing() { return phase==Phase.ALARM; }
    /** Feed one observation; {@code elapsed} is the number of ticks since the previous one. */
    public Signal observe(int sighted,int threshold,long now,int elapsed) {
        if(phase==Phase.CALM) return sighted>=Math.max(1,threshold) ? Signal.DISPATCH : Signal.NONE;
        quietTicks=sighted>0 ? 0 : quietTicks+elapsed;
        if(phase==Phase.RAISING) {
            // The threat may vanish before the bell rings; a late runner is replaced by the next check.
            if(quietTicks>=ALL_CLEAR_TICKS || now>=deadline) { calm(); return Signal.STAND_DOWN; }
            return Signal.NONE;
        }
        if(quietTicks>=ALL_CLEAR_TICKS) { calm(); return Signal.ALL_CLEAR; }
        return Signal.NONE;
    }
    public void dispatched(long now) { phase=Phase.RAISING; deadline=now+RUN_TICKS; quietTicks=0; }
    /** The bell rang; returns false when the alarm was already sounding. */
    public boolean ring() {
        boolean raised=phase!=Phase.ALARM;
        phase=Phase.ALARM; quietTicks=0; return raised;
    }
    public void calm() { phase=Phase.CALM; quietTicks=0; deadline=0; }
}
