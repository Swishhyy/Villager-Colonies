package io.github.swishhyy.wwmc.core;

import java.util.Collection;
import java.util.HashSet;
import java.util.UUID;

public final class CitizenNames {
    private static final String[] FIRST={"Ada","Alden","Alice","Anya","Arthur","Bram","Cora","Dorian","Edith","Elias","Elise","Emery","Finn","Freya","Gareth","Hazel","Hugo","Iris","Jasper","Jonah","Lena","Leona","Mara","Miles","Milo","Nora","Oren","Oscar","Petra","Quinn","Rhea","Rowan","Silas","Tessa","Theo","Vera","Willa","Wyatt","Yara","Zane"};
    private static final String[] LAST={"Ashford","Birch","Brook","Clay","Dale","Ember","Fen","Field","Flint","Fox","Grove","Hart","Hill","Hollow","Marsh","Moss","Oak","Pike","Reed","Ridge","River","Rowe","Shaw","Stone","Vale","Ward","Wells","West","Willow","Wood"};
    private CitizenNames() {}
    public static String choose(UUID citizen,Collection<String> used) {
        var taken=new HashSet<>(used);
        int count=FIRST.length*LAST.length;
        int start=Math.floorMod(citizen.hashCode(),count);
        for(int offset=0;offset<count;offset++) {
            int index=(start+offset)%count;
            String name=FIRST[index/LAST.length]+" "+LAST[index%LAST.length];
            if(!taken.contains(name)) return name;
        }
        return "Traveler "+citizen.toString().substring(0,8);
    }
    public static boolean numbered(String name) { return name.matches("Citizen [0-9]+"); }
}
