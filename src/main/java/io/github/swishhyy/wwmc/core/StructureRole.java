package io.github.swishhyy.wwmc.core;

/** Stations declare purpose; furniture supplies capacity. */
public enum StructureRole {
    HOUSING("housing", true, false), BARRACKS("barracks", true, false),
    HOSPITAL("hospital", false, false), WAREHOUSE("warehouse", false, false),
    FARM("farm", false, true), LUMBER("lumber", false, true), MINE("mine", false, true), QUARRY("quarry", false, true), GUARD("guard", false, true),
    CRAFTSMAN("craftsman", false, true);
    private final String id;
    private final boolean residential;
    private final boolean worker;
    StructureRole(String id, boolean residential, boolean worker) {
        this.id = id; this.residential = residential; this.worker = worker;
    }
    public String id() { return id; }
    public boolean providesHousing() { return residential; }
    public boolean detectsBeds() { return residential || this==HOSPITAL; }
    public boolean providesWork() { return worker; }
    public boolean excavates() { return this==MINE || this==QUARRY; }
}
