package emu.grasscutter.game.reaction;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public enum BaseReaction {

    None (0),
    Burning (3),
    Overload (1),
    ElectroCharged (14),
    Superconduct (16),
    SwirlPyro (17),
    SwirlElectro (19),
    SwirlHydro (18),
    SwirlCryo (20),
    Shatter (31),
    Burgeon (36);

    private final int value;
    private static final Int2ObjectMap<BaseReaction> map = new Int2ObjectOpenHashMap<>();
    private static final Map<String, BaseReaction> stringMap = new HashMap<>();

    static {
        Stream.of(values()).forEach(e -> {
            map.put(e.getValue(), e);
            stringMap.put(e.name(), e);
        });
    }

    private BaseReaction(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static BaseReaction getTypeByValue(int value) {
        return map.getOrDefault(value, None);
    }

    public static BaseReaction getTypeByName(String name) {
        return stringMap.getOrDefault(name, None);
    }
}
