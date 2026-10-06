package io.github.swishhyy.wwmc.core;

import java.util.OptionalInt;
import java.util.function.IntUnaryOperator;

public final class AutomaticDepth {
    private AutomaticDepth() {}
    public static OptionalInt choose(int configuredMin,int configuredMax,int worldMin,int entranceY,IntUnaryOperator random) {
        int lower=Math.max(worldMin+2,Math.min(configuredMin,configuredMax));
        int upper=Math.min(entranceY-1,Math.max(configuredMin,configuredMax));
        if(lower>upper) return OptionalInt.empty();
        return OptionalInt.of(lower+random.applyAsInt(upper-lower+1));
    }
}
