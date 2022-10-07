package emu.grasscutter.game.props;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public enum AmplificationType {

    None (0),
    Vaporize (2),
    Melt (7),
    Aggravate (34),
    Spread (35);

    private final int value;

    private static final Int2ObjectMap<AmplificationType> map = new Int2ObjectOpenHashMap<>();
    private static final Map<String, AmplificationType> stringMap = new HashMap<>();

    static {
        Stream.of(values()).forEach(e -> {
            map.put(e.getValue(), e);
            stringMap.put(e.name(), e);
        });
    }

    private AmplificationType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static AmplificationType getTypeByValue(int value) {
        return map.getOrDefault(value, None);
    }

    public static AmplificationType getTypeByName(String name) {
        return stringMap.getOrDefault(name, None);
    }
}
