package emu.grasscutter.utils;

import ch.qos.logback.classic.Logger;
import emu.grasscutter.game.reaction.AbilityReaction;
import emu.grasscutter.game.reaction.BaseReaction;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.slf4j.LoggerFactory;

public class GrassLogger {
    private static final Logger log = (Logger) LoggerFactory.getLogger(GrassLogger.class);

    private static Int2IntMap reactionMap = new Int2IntOpenHashMap();

    public static void UpdateReactionMap(int reactionID, int entityID) {
        reactionMap.put(reactionID, entityID);
    }

    public static int GetReactionEntity(int reactionID) {
        String ar = AbilityReaction.getTypeByValue(reactionID).toString();
        int br = BaseReaction.getTypeByName(ar).getValue();
        return reactionMap.getOrDefault(br, -1);
    }

    public static void Log(String text) {
        log.info(text);
    }
}
