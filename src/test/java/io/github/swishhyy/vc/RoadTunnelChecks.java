package io.github.swishhyy.vc;

import io.github.swishhyy.vc.worldgen.RoadNetwork;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Old roads keep a gentle grade through steep hills in tunnels, and keep to the land everywhere else. */
public final class RoadTunnelChecks {
    private static int checks;
    private static void check(boolean ok,String message) { checks++; if(!ok) throw new AssertionError(message); }
    /** Heights along a course, laid a stretch at a time from 64. */
    private static final class Land {
        final List<Integer> heights=new ArrayList<>();
        int last() { return heights.isEmpty() ? 64 : heights.getLast(); }
        Land flat(int length) { int h=last(); for(int i=0;i<length;i++) heights.add(h); return this; }
        Land slope(int rise) { int h=last(),step=Integer.signum(rise); for(int i=0;i<Math.abs(rise);i++) heights.add(h+=step); return this; }
        Land jump(int rise) { heights.add(last()+rise); return this; }
        int[] natural() { return heights.stream().mapToInt(Integer::intValue).toArray(); }
    }
    private static RoadNetwork.Profile grade(int[] natural,boolean tunnels) { return RoadNetwork.grade(natural,new boolean[natural.length],tunnels); }
    private static List<int[]> tunnels(RoadNetwork.Profile profile) {
        List<int[]> runs=new ArrayList<>();
        boolean[] tunnel=profile.tunnel();
        for(int i=0;i<tunnel.length;i++) if(tunnel[i] && (i==0 || !tunnel[i-1])) {
            int end=i; while(end+1<tunnel.length && tunnel[end+1]) end++;
            runs.add(new int[]{i,end});
        }
        return runs;
    }
    /** No step of more than a block, open road within four blocks of the land, and every tunnel long enough and under rock. */
    private static List<int[]> steady(String land,int[] natural,RoadNetwork.Profile profile) {
        int[] feet=profile.feet();
        for(int i=1;i<feet.length;i++) check(Math.abs(feet[i]-feet[i-1])<=1,land+": a step of "+(feet[i]-feet[i-1])+" at "+i);
        for(int i=0;i<feet.length;i++) if(!profile.tunnel()[i])
            check(natural[i]-feet[i]<=4 && feet[i]-natural[i]<=4,land+": the open road cuts or fills more than four blocks at "+i);
        var runs=tunnels(profile);
        for(int[] run:runs) {
            int deepest=0;
            for(int i=run[0];i<=run[1];i++) { deepest=Math.max(deepest,natural[i]-feet[i]); check(natural[i]>=feet[i],land+": a tunnel above the land at "+i); }
            check(run[1]-run[0]+1>=8 && run[1]-run[0]+1<=48,land+": a tunnel of "+(run[1]-run[0]+1)+" blocks");
            check(deepest>=7,land+": a tunnel with no rock over its roof");
            check(run[0]>=12 && run[1]<=feet.length-13,land+": a tunnel beside a gate");
        }
        return runs;
    }

    @Test void steepHillIsTunnelledAtAGentleGrade() {
        int[] hill=new Land().flat(40).slope(20).flat(10).slope(-20).flat(40).natural();
        var profile=grade(hill,true);
        var runs=steady("A hill",hill,profile);
        check(runs.size()==1 && runs.getFirst()[0]<65 && runs.getFirst()[1]>65,"One tunnel runs under the summit");
        int climb=0;
        for(int i=1;i<hill.length;i++) climb+=Math.max(0,profile.feet()[i]-profile.feet()[i-1]);
        check(climb<=8,"The road climbs only a little of the hill: "+climb);
        int[] ridge=new Land().flat(40).slope(12).slope(-12).flat(40).natural();
        check(steady("A ridge",ridge,grade(ridge,true)).size()==1,"A narrow ridge is tunnelled too");
        System.out.println("Passed "+checks+" hill checks.");
    }

    @Test void cliffIsClimbedInsideNotStepped() {
        int[] cliff=new Land().flat(40).jump(20).flat(89).natural();
        int[] old=grade(cliff,false).feet();
        boolean wall=false;
        for(int i=1;i<old.length;i++) wall|=Math.abs(old[i]-old[i-1])>1;
        check(wall,"Without tunnels the road meets the cliff with a step");
        var runs=steady("A cliff",cliff,grade(cliff,true));
        check(runs.size()==1 && runs.getFirst()[0]<=42,"The tunnel opens at the foot of the cliff");
        int[] high=new Land().flat(40).jump(40).flat(89).natural();
        check(steady("A high cliff",high,grade(high,true)).size()==1,"Even a high cliff is climbed inside");
        System.out.println("Passed "+checks+" cliff checks.");
    }

    @Test void gentleLandAndTallPlateausKeepTheOpenRoad() {
        int[] meadow=new int[140];
        for(int i=0;i<meadow.length;i++) meadow[i]=64+(int)Math.round(2.5*Math.sin(i/6.0)+1.5*Math.sin(i/2.7+1.0)+Math.sin(i/11.0));
        check(Arrays.equals(grade(meadow,true).feet(),grade(meadow,false).feet()) && tunnels(grade(meadow,true)).isEmpty(),
                "A rolling meadow road is not tunnelled");
        int[] plateau=new Land().flat(40).slope(40).flat(90).natural();
        check(tunnels(grade(plateau,true)).isEmpty() && Arrays.equals(grade(plateau,true).feet(),grade(plateau,false).feet()),
                "A climb onto a tall plateau keeps its steps rather than running far underground");
        int[] lower=new Land().flat(40).slope(30).flat(90).natural();
        check(steady("A plateau",lower,grade(lower,true)).size()==1,"A lower plateau is climbed in a tunnel at a gentler grade");
        int[] ledge=new Land().flat(40).slope(8).jump(12).slope(6).flat(90).natural();
        check(steady("A ledge",ledge,grade(ledge,true)).size()==1,"A plateau behind a cliff is climbed in a tunnel");
        int[] gate=new Land().flat(6).slope(20).flat(10).slope(-20).flat(40).natural();
        steady("A hill by a gate",gate,grade(gate,true));
        System.out.println("Passed "+checks+" open road checks.");
    }
}
