package io.github.swishhyy.wwmc.core;

/**
 * Emerald upgrades. A station's range level widens the cube it works in and its crew level adds worker slots; a town's
 * population level raises its citizen limit and makes its enemy waves larger. These are the pure rules, shared by the
 * server and the client's range preview.
 */
public final class Upgrades {
    /** Highest range, crew or yield level a station can buy. */
    public static final int MAX_STATION_LEVEL=3;
    public enum Kind {
        RANGE("Range"),CREW("Crew"),YIELD("Yield");
        private final String title;
        Kind(String title) { this.title=title; }
        public String title() { return title; }
        public boolean supports(StructureRole role) {
            return switch(this) { case RANGE -> widens(role); case CREW -> hires(role); case YIELD -> yields(role); };
        }
    }
    /** An enchanter looks for its table farther out than other stations reach. */
    public static final int ENCHANTER_RADIUS=5;
    private Upgrades() {}
    public static int baseRadius(StructureRole role) { return role==StructureRole.ENCHANTER ? ENCHANTER_RADIUS : StationRange.RADIUS; }
    /** Blocks the station reaches in each direction at this range level. */
    public static int radius(StructureRole role,int level) { return baseRadius(role)+Math.clamp(level,0,MAX_STATION_LEVEL); }
    /** Quarries dig a whole chunk, mines follow their vein or tunnels, couriers cross the town and craftsmen work at their bench: a range upgrade means nothing to them. */
    public static boolean widens(StructureRole role) {
        return role!=StructureRole.QUARRY && role!=StructureRole.MINE && role!=StructureRole.COURIER && role!=StructureRole.CRAFTSMAN && role!=StructureRole.TRADER;
    }
    /** Every job block except a quarry takes exactly one worker. */
    public static boolean soloCrew(StructureRole role) { return role.providesWork() && role!=StructureRole.QUARRY; }
    /** Quarries alone can buy additional worker slots. */
    public static boolean hires(StructureRole role) { return role==StructureRole.QUARRY; }
    public static boolean yields(StructureRole role) { return role==StructureRole.FARM || role==StructureRole.MINE; }
    public static int yieldPercent(int level) { return 10*Math.clamp(level,0,MAX_STATION_LEVEL); }
    /** The next station level costs the base for the first, then twice the previous price. */
    public static int stationCost(int base,int level) { return Math.max(0,base)<<Math.clamp(level,0,MAX_STATION_LEVEL); }
    /** Each population level costs the base more than the one before: base, 2 × base, 3 × base... */
    public static int populationCost(int base,int level) { return (int)Math.min(Integer.MAX_VALUE,(long)Math.max(0,base)*(Math.max(0,level)+1L)); }
    public static int populationLimit(int level,int base,int step,int ceiling) {
        return (int)Math.max(1,Math.min(ceiling,(long)base+(long)Math.max(0,level)*Math.max(1,step)));
    }
    /** Levels a town can buy before its limit reaches the ceiling. */
    public static int maxPopulationLevel(int base,int step,int ceiling) { return levelFor(ceiling,base,step); }
    /** Fewest levels whose limit covers this many citizens; towns from before upgrades keep the people they have. */
    public static int levelFor(int citizens,int base,int step) {
        int over=citizens-base;
        return over<=0 ? 0 : (over+Math.max(1,step)-1)/Math.max(1,step);
    }
    /** Emeralds and emerald blocks taken for a price, and the emeralds given back as change. */
    public record Payment(int emeralds,int blocks,int change) {}
    /** Loose emeralds go first, then emerald blocks at nine each with change. Null when the purse is short. */
    public static Payment pay(int emeralds,int blocks,int cost) {
        if(cost<=0) return new Payment(0,0,0);
        if((long)Math.max(0,emeralds)+9L*Math.max(0,blocks)<cost) return null;
        int loose=Math.min(Math.max(0,emeralds),cost),rest=cost-loose,fromBlocks=(rest+8)/9;
        return new Payment(loose,fromBlocks,fromBlocks*9-rest);
    }
}
