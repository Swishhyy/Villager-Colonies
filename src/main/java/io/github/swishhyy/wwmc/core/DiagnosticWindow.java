package io.github.swishhyy.wwmc.core;

import java.util.Objects;

/** One loaded citizen's sustained problem, with delayed warnings and a cooldown across changing problems. */
public final class DiagnosticWindow {
    public enum Signal { QUIET, WARNING, RESOLVED }
    private String problem;
    private long since,lastSample=Long.MIN_VALUE,lastWarning=Long.MIN_VALUE,clearSince=-1;
    private boolean reported;

    /** Samples arrive once per second. An unloading gap must not count toward a sustained failure. */
    public Signal sample(String current,long now,long delay,long repeat) {
        if(lastSample!=Long.MIN_VALUE && (now<lastSample || now-lastSample>40)) reset();
        if(lastWarning!=Long.MIN_VALUE && now<lastWarning) lastWarning=Long.MIN_VALUE;
        lastSample=now;
        if(current==null) {
            if(problem==null) return Signal.QUIET;
            if(clearSince<0) clearSince=now;
            // A meal, a short walk or a single successful probe must not announce a false recovery.
            if(now-clearSince<200) return Signal.QUIET;
            boolean resolved=reported;
            problem=null; reported=false; clearSince=-1;
            return resolved ? Signal.RESOLVED : Signal.QUIET;
        }
        clearSince=-1;
        if(!Objects.equals(problem,current)) { problem=current; since=now; reported=false; }
        if(now-since<delay || lastWarning!=Long.MIN_VALUE && now-lastWarning<repeat) return Signal.QUIET;
        lastWarning=now; reported=true;
        return Signal.WARNING;
    }
    public long blockedTicks(long now) { return problem==null ? 0 : Math.max(0,now-since); }
    /** Disabling diagnostics, sleeping or leaving duty forgets a pending issue without claiming it was repaired. */
    public void reset() { problem=null; lastSample=Long.MIN_VALUE; clearSince=-1; reported=false; }
}
