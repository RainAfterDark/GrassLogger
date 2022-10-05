package emu.grasscutter.game.reaction;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public enum AbilityReaction {

    None (0),
    Burning (6),
    Overload (10),
    ElectroCharged (14),
    Superconduct (19),
    SwirlPyro (20),
    SwirlElectro (21),
    SwirlHydro (22),
    SwirlCryo (23),
    Shatter (31),
    Burgeon (37);

    private final int value;

    private static final Int2ObjectMap<AbilityReaction> map = new Int2ObjectOpenHashMap<>();
    private static final Map<String, AbilityReaction> stringMap = new HashMap<>();

    static {
        Stream.of(values()).forEach(e -> {
            map.put(e.getValue(), e);
            stringMap.put(e.name(), e);
        });
    }

    private AbilityReaction(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static AbilityReaction getTypeByValue(int value) {
        return map.getOrDefault(value, None);
    }

    public static AbilityReaction getTypeByName(String name) {
        return stringMap.getOrDefault(name, None);
    }
}
