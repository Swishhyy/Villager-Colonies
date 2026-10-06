package io.github.swishhyy.wwmc;

import io.github.swishhyy.wwmc.core.*;
import java.util.*;
import org.junit.jupiter.api.Test;

public final class CitizenPolicyChecks {
    private static int checks;
    private static void check(boolean ok,String message) { checks++; if(!ok) throw new AssertionError(message); }
    @Test void namesShiftsAndDepth() { main(new String[0]); }
    public static void main(String[] ignored) {
        check(!ShiftClock.night(12999) && ShiftClock.night(13000),"The night shift starts at sunset");
        check(ShiftClock.night(22999) && !ShiftClock.night(23000),"The day shift resumes at dawn");
        check(ShiftClock.night(24000+13000) && ShiftClock.night(-11000),"Shifts survive multiple days and clock wrapping");
        check(AutomaticDepth.choose(-30,10,-64,65,b -> 0).getAsInt()==-30,"A mine can choose the lower endpoint");
        check(AutomaticDepth.choose(-30,10,-64,65,b -> b-1).getAsInt()==10,"A mine can choose the upper endpoint");
        check(AutomaticDepth.choose(-30,10,-64,0,b -> b-1).getAsInt()==-1,"A mine must descend below its entrance");
        check(AutomaticDepth.choose(-30,10,-64,-30,b -> 0).isEmpty(),"An entrance below the entire band cannot make an upward mine");
        check(AutomaticDepth.choose(10,-30,-64,65,b -> 0).getAsInt()==-30,"Reversed configured endpoints are normalized");
        check(AutomaticDepth.choose(-60,-40,-50,65,b -> 0).getAsInt()==-48,"The plan keeps two blocks of room above the world floor");
        Set<String> names=new HashSet<>();
        for(int i=0;i<128;i++) names.add(CitizenNames.choose(new UUID(0,i),names));
        check(names.size()==128 && names.stream().noneMatch(CitizenNames::numbered),"A full settlement receives distinct personal names");
        UUID id=new UUID(17,91);
        check(CitizenNames.choose(id,List.of()).equals(CitizenNames.choose(id,List.of())),"A citizen's initial name is stable");
        String first=CitizenNames.choose(id,List.of());
        check(!CitizenNames.choose(id,List.of(first)).equals(first),"Reserved names also prevent collisions with unloaded citizens");
        check(CitizenNames.numbered("Citizen 123") && !CitizenNames.numbered("Citizen Kane"),"Only old generated number labels are migrated");
        System.out.println("Passed "+checks+" citizen policy checks.");
    }
}
