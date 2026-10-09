package io.github.swishhyy.wwmc;

import io.github.swishhyy.wwmc.core.DiagnosticWindow;
import io.github.swishhyy.wwmc.settlement.TownNeeds;
import org.junit.jupiter.api.Test;
import static io.github.swishhyy.wwmc.core.DiagnosticWindow.Signal.*;
import static org.junit.jupiter.api.Assertions.*;

public final class DiagnosticChecks {
    private static final long DELAY=2400,REPEAT=6000;
    @Test void fullBarrelsAndMissingMaterialsAreNeedsButNormalWorkIsQuiet() {
        for(String message:new String[]{"The job's barrels are full","Warehouse is full; keeping supplies in my inventory",
                "Waiting for this item's matching repair material","Waiting for 3 lapis in my barrel","Cannot reach 2 job barrels",
                "Waiting for courier-delivered processing inputs"}) assertTrue(TownNeeds.asks(message),message);
        for(String message:new String[]{"Waiting for the iron vein to replenish (15s)","Orders are stocked, or waiting for courier-delivered materials",
                "Waiting for couriers to bring damaged equipment and matching repair materials","Waiting for crops to grow",
                "Off duty; resting","Hospital ready; no patient awaiting treatment"}) assertFalse(TownNeeds.asks(message),message);
    }
    private static void quiet(DiagnosticWindow window,String issue,long start,long end) {
        for(long now=start;now<end;now+=20) assertEquals(QUIET,window.sample(issue,now,DELAY,REPEAT),"Unexpected log at tick "+now);
    }
    @Test void delaysWarningsAndLimitsRepeatsWhileActivitiesChange() {
        var window=new DiagnosticWindow();
        quiet(window,"town|station",0,DELAY);
        assertEquals(WARNING,window.sample("town|station",DELAY,DELAY,REPEAT));
        quiet(window,"town|station",DELAY+20,DELAY+REPEAT);
        assertEquals(WARNING,window.sample("town|station",DELAY+REPEAT,DELAY,REPEAT));
        assertEquals(DELAY+REPEAT,window.blockedTicks(DELAY+REPEAT));
        assertEquals(QUIET,window.sample("town|other-station",DELAY+REPEAT+20,DELAY,REPEAT));
        quiet(window,"town|other-station",DELAY+REPEAT+40,DELAY+REPEAT*2);
        assertEquals(WARNING,window.sample("town|other-station",DELAY+REPEAT*2,DELAY,REPEAT),"A job change cannot bypass the citizen's warning cooldown");
    }
    @Test void aBriefPauseDoesNotPretendAStallWasFixed() {
        var window=new DiagnosticWindow();
        quiet(window,"town|station",0,DELAY);
        assertEquals(WARNING,window.sample("town|station",DELAY,DELAY,REPEAT));
        quiet(window,null,DELAY+20,DELAY+100);
        assertEquals(QUIET,window.sample("town|station",DELAY+100,DELAY,REPEAT));
        quiet(window,null,DELAY+120,DELAY+320);
        assertEquals(RESOLVED,window.sample(null,DELAY+320,DELAY,REPEAT));
        quiet(window,null,DELAY+340,DELAY+1000);
        assertEquals(0,window.blockedTicks(DELAY+1000));
    }
    @Test void unloadingAndOffDutyResetTheDelayWithoutBypassingTheCooldown() {
        var window=new DiagnosticWindow();
        quiet(window,"town|station",0,DELAY-20);
        assertEquals(QUIET,window.sample("town|station",12000,DELAY,REPEAT),"Unloaded time is not time spent stalled");
        quiet(window,"town|station",12020,14400);
        assertEquals(WARNING,window.sample("town|station",14400,DELAY,REPEAT));
        window.reset();
        quiet(window,"town|station",14420,20400);
        assertEquals(WARNING,window.sample("town|station",20400,DELAY,REPEAT),"Leaving duty resets the pending issue but preserves the warning cooldown");
    }
    @Test void aWorldClockResetCannotLeaveWarningsSuppressedForever() {
        var window=new DiagnosticWindow();
        quiet(window,"town|station",0,DELAY);
        assertEquals(WARNING,window.sample("town|station",DELAY,DELAY,REPEAT));
        quiet(window,"town|station",0,DELAY);
        assertEquals(WARNING,window.sample("town|station",DELAY,DELAY,REPEAT));
    }
}
