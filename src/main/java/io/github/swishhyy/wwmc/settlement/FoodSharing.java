package io.github.swishhyy.wwmc.settlement;

import java.util.Map;
import java.util.UUID;

/** Scarce meals stay communal. Among hungry citizens, the least recently fed get the next turn. */
public final class FoodSharing {
    public static final int PERSONAL_LIMIT=1;
    private FoodSharing() {}
    public static boolean scarce(int meals,int population) { return meals<Math.max(1,population)*2; }
    public static int spareLimit(int meals,int population) { return scarce(meals,population) ? 0 : PERSONAL_LIMIT; }
    public static boolean mayTake(UUID citizen,long lastMeal,Map<UUID,Long> hungry,int meals,int population) {
        if(meals<=0) return false;
        return !scarce(meals,population) || hungry.entrySet().stream()
                .noneMatch(e -> !e.getKey().equals(citizen) && e.getValue()<lastMeal);
    }
}
