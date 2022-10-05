package emu.grasscutter.utils;

import ch.qos.logback.classic.Logger;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.reaction.AbilityReaction;
import emu.grasscutter.game.reaction.BaseReaction;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.proto.AttackResultOuterClass.AttackResult;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.slf4j.LoggerFactory;

public class GrassLogger {
    private static final Logger log = (Logger) LoggerFactory.getLogger(GrassLogger.class);

    private static Int2IntMap reactionMap = new Int2IntOpenHashMap();

    public static void UpdateReactionMap(int reactionID, int entityID) {
        if (!reactionMap.containsKey(reactionID))
            reactionMap.put(reactionID, entityID);
        else
            reactionMap.replace(reactionID, entityID);
        //Log(reactionID + " " + entityID);
    }

    public static int GetReactionEntity(int reactionID) {
        String ar = AbilityReaction.getTypeByValue(reactionID).toString();
        int br = BaseReaction.getTypeByName(ar).getValue();
        //Log(ar + " " + br + " " + reactionMap.getOrDefault(br, -1));
        return reactionMap.getOrDefault(br, -1);
    }

    public static void parseAttackResult(Scene scene, AttackResult result) {
        GameEntity attacker = scene.getEntityById(result.getAttackerId());
        GameEntity target = scene.getEntityById(result.getDefenseId());
        //Log(attacker.getId() + " " + target.getId());
    }

    public static void Log(String text) {
        log.info(text);
    }
}
