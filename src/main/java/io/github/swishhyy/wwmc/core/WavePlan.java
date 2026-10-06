package io.github.swishhyy.wwmc.core;

import java.util.EnumMap;
import java.util.function.IntUnaryOperator;

/** Population-driven enemy wave sizes, compositions and timing. */
public final class WavePlan {
    public enum Attacker { ZOMBIE, SKELETON, SPIDER }
    private static final String[] COMPASS={"north","north-east","east","south-east","south","south-west","west","north-west"};
    private WavePlan() {}
    public static int size(int population,int base,double perCitizen,int max) {
        long scaled=(long)base+(long)Math.ceil(Math.max(0,population)*Math.max(0.0,perCitizen));
        return (int)Math.max(1,Math.min(Math.max(1,max),scaled));
    }
    /** Small towns face zombies; larger towns also draw archers and then spiders. */
    public static EnumMap<Attacker,Integer> compose(int size,int population) {
        EnumMap<Attacker,Integer> wave=new EnumMap<>(Attacker.class);
        int skeletons=population>=6 ? size/4 : 0;
        int spiders=population>=10 ? size*3/20 : 0;
        wave.put(Attacker.ZOMBIE,size-skeletons-spiders);
        wave.put(Attacker.SKELETON,skeletons);
        wave.put(Attacker.SPIDER,spiders);
        return wave;
    }
    /** Days between waves with up to a quarter of jitter either way; {@code random} returns [0,bound). */
    public static long delay(int days,IntUnaryOperator random) {
        long base=Math.max(1,days)*24000L;
        int spread=(int)(base/2);
        return base-spread/2+random.applyAsInt(spread+1);
    }
    /** Minecraft compass: north is negative Z and east is positive X. */
    public static String compass(double dx,double dz) {
        double degrees=Math.toDegrees(Math.atan2(dx,-dz));
        return COMPASS[Math.floorMod((int)Math.round(degrees/45.0),8)];
    }
}
