package io.github.swishhyy.wwmc.core;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** One sentry per station in peace; the full assigned crew responds to an alarm. */
public final class GuardDuty {
    private GuardDuty() {}
    public static boolean active(Collection<UUID> crew,UUID guard,boolean night,boolean alarm) {
        if(!crew.contains(guard)) return false;
        if(alarm) return true;
        List<UUID> ordered=crew.stream().sorted().toList();
        return guard.equals(ordered.get(night && ordered.size()>1 ? 1 : 0));
    }
}
