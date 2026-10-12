package io.github.swishhyy.vc.core;

import java.util.EnumMap;
import java.util.function.IntUnaryOperator;

/** Population-driven enemy wave sizes, compositions and timing. A town's population upgrades add threat. */
public final class WavePlan {
    public enum Attacker { FOOT_SOLDIER, ARCHER, ARMORED }
    private static final String[] COMPASS={"north","north-east","east","south-east","south","south-west","west","north-west"};
    private WavePlan() {}
    public static int size(int population,int base,double perCitizen,int max) { return size(population,base,perCitizen,max,0,0); }
    /** Each threat level (a population upgrade) adds {@code perThreat} attackers, and raises the largest wave by as many. */
    public static int size(int population,int base,double perCitizen,int max,int threat,int perThreat) {
        long extra=(long)Math.max(0,threat)*Math.max(0,perThreat);
        long scaled=(long)base+(long)Math.ceil(Math.max(0,population)*Math.max(0.0,perCitizen))+extra;
        return (int)Math.max(1,Math.min(Math.max(1,max)+extra,Math.min(Integer.MAX_VALUE,scaled)));
    }
    public static EnumMap<Attacker,Integer> compose(int size,int population) { return compose(size,population,0); }
    /** Without saved raid history, begin with a melee scouting party. */
    public static EnumMap<Attacker,Integer> compose(int size,int population,int threat) { return compose(size,population,threat,0); }
    /** The first two raids are melee; surviving towns later draw archers, then armored veterans. */
    public static EnumMap<Attacker,Integer> compose(int size,int population,int threat,int previousRaids) {
        EnumMap<Attacker,Integer> wave=new EnumMap<>(Attacker.class);
        size=Math.max(1,size);
        int archers=previousRaids>=2 && population>=6 && size>=3 ? Math.max(1,size/4) : 0;
        int armored=previousRaids>=5 && (population>=12 || threat>=2) && size>=4 ? Math.max(1,size/5) : 0;
        wave.put(Attacker.FOOT_SOLDIER,size-archers-armored);
        wave.put(Attacker.ARCHER,archers);
        wave.put(Attacker.ARMORED,armored);
        return wave;
    }
    /** Days between raids with up to 40% variation either way; {@code random} returns [0,bound). */
    public static long delay(int days,IntUnaryOperator random) {
        long base=Math.max(1,days)*24000L;
        int spread=(int)(base*4/5);
        return base-spread/2+random.applyAsInt(spread+1);
    }
    /** Minecraft compass: north is negative Z and east is positive X. */
    public static String compass(double dx,double dz) {
        double degrees=Math.toDegrees(Math.atan2(dx,-dz));
        return COMPASS[Math.floorMod((int)Math.round(degrees/45.0),8)];
    }
}
