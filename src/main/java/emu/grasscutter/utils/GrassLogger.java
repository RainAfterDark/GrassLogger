package emu.grasscutter.utils;

import static emu.grasscutter.config.Configuration.*;

import ch.qos.logback.classic.Logger;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.data.excels.MonsterData;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityClientGadget;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.proto.AbilityControlBlockOuterClass.AbilityControlBlock;
import emu.grasscutter.net.proto.AbilityEmbryoOuterClass.AbilityEmbryo;
import emu.grasscutter.net.proto.AttackResultOuterClass.AttackResult;

import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

public class GrassLogger {
    private static final Logger log = (Logger) LoggerFactory.getLogger(GrassLogger.class);
    private static int uidCount = 0;
    private static long lastTime = 0;
    private static Int2IntMap reactionMap = new Int2IntOpenHashMap();
    private static Int2ObjectMap<EntityAvatar> avatarMap = new Int2ObjectLinkedOpenHashMap<>();
    private static Map<String, AbilityControlBlock> abilityMap = new HashMap<>();
    private static Long2ObjectMap<MonsterAffix> monsterAffixMap = new Long2ObjectOpenHashMap<>();
    private static Int2ObjectMap<String> monsterNameMap = new Int2ObjectOpenHashMap<>();

    private static String getUID() {return Integer.toString(++uidCount);}
    private static String getDeltaTime(long currentTime) {
        long deltaTime = currentTime - lastTime;
        if (lastTime == 0) deltaTime = 0;
        lastTime = currentTime;
        return Long.toString(deltaTime);
    }

    private static String getElementName(int elementId) {
        String[] elementNames = {
            "Physical", "Pyro", "Hydro", "Dendro", "Electro", "Cryo",
            "Frozen", "Anemo", "Geo", "AntiFire", "VehicleMuteIce",
            "Mushroom", "Overdose", "Wood", "COUNT"
        };
        if (elementId == 255) return "Default";
        return elementNames[elementId];
    }

    private static EntityType getEntityType(int id) {return EntityType.getTypeByValue(id >> 24);}

    private static String getAvatarName(int id) {return avatarMap.get(id).getAvatar().getAvatarData().getName();}

    private static class MonsterAffix {
        private static int letterCount = 0;
        public String letter;
        public int count;

        public MonsterAffix() {
            int n = letterCount++;
            String letter = "";
            do {
                int d = n % 26;
                n = n / 26;
                letter += (char) (65 + d);
            } while (n > 0);
            this.letter = letter;
            this.count = 1;
        }
    }

    private static String getMonsterAffix(long hash) {
        MonsterAffix affix;
        if (monsterAffixMap.containsKey(hash)) {
            affix = monsterAffixMap.get(hash);
            affix.count++;
        } else {
            affix = new MonsterAffix();
            monsterAffixMap.put(hash, affix);
        }
        return affix.letter + affix.count;
    }

    private static String getMonsterName(EntityMonster monster) {
        int id = monster.getId();
        if (monsterNameMap.containsKey(id)) return monsterNameMap.get(id);
        MonsterData monsterData = monster.getMonsterData();
        long hash = monsterData.getDescribeData().getNameTextMapHash();
        String affix = getMonsterAffix(hash);
        String name = affix + " " + monsterData.getMonsterName(); //preferably this should take from the name hash, but fuck TextMaps
        monsterNameMap.put(id, name);
        return name;
    }

    private static String getAbilityName(String avatarName, int aid) {
        AbilityControlBlock abilities = abilityMap.get(avatarName);
        AbilityEmbryo embryo = abilities.getAbilityEmbryoList(aid - embryoIndexOffset - 1);
        if (embryo != null) {
            int hash = embryo.getAbilityNameHash();
            String name = GameData.getAbilityHashes().get(hash);
            if (name != null) return name;
            return Integer.toString(hash);
        }
        return Integer.toString(aid);
    }

    private static String getGadgetName(int configId) {
        GadgetData gadget = GameData.getGadgetDataMap().get(configId);
        if (gadget != null) {
            String name = gadget.getJsonName();
            if (name != null) return name;
        }
        return Integer.toString(configId);
    }

    private static String getRoot(Scene scene, int id) {
        EntityType type = getEntityType(id);

        switch (type) {
            case Avatar -> {return getAvatarName(id);}
            case Monster -> {
                EntityMonster monster = (EntityMonster) scene.getEntityById(id);
                return getMonsterName(monster);
            }
            case Level -> {return "World";}
            case Team -> {return "Team";}
            default -> {
                if (scene.getEntityById(id) instanceof EntityClientGadget) {
                    EntityClientGadget gadget = (EntityClientGadget) scene.getEntityById(id);
                    return getRoot(scene, gadget.getOriginalOwnerEntityId());
                }
                return Integer.toString(id);
            }
        }
    }

    private static String getAttacker(Scene scene, int attackerId, int casterId, int aid) {
        if (getEntityType(attackerId) == EntityType.Gadget || getEntityType(casterId) == EntityType.Gadget) {
            String ar = AbilityReaction.getTypeByValue(aid).toString();
            int br = BaseReaction.getTypeByName(ar).getValue();
            //Log(ar + " " + br + " " + reactionMap.getOrDefault(br, -1));
            return getRoot(scene, reactionMap.get(br));
        }
        return getRoot(scene, attackerId);
    }

    private static String getSource(Scene scene, int attackerId, int casterId, ElementType element, int aid, int defenseId) {
        EntityType type = getEntityType(attackerId);

        if (type == EntityType.Gadget || getEntityType(casterId) == EntityType.Gadget) {
            return "Reaction";
        }

        if (attackerId == defenseId) {
            if (element == ElementType.None) {
                return "Fall Damage";
            }
            return "Self-Inflicted";
        }

        if (scene.getEntityById(attackerId) instanceof EntityClientGadget) {
            return getGadgetName(((EntityClientGadget) scene.getEntityById(attackerId)).getGadgetId());
        }

        if (type == EntityType.Avatar) {
            if (aid > embryoIndexOffset) {
                return getAbilityName(getAvatarName(attackerId), aid);
            }
            return "Direct";
        } else if (type == EntityType.Level) {
            return "Environment";
        }

        return type.toString();
    }

    private static String getReaction(int aid, int mid, ElementType element, String attacker) {
        if (element == ElementType.Grass) {
            if (aid == 2 && mid == 5) {
                return "Bloom";
            } else if (aid == 1 && mid == 2 &&
                !Objects.equals(attacker, "Collei")) {
                return "Hyperbloom";
            }
        }

        AbilityReaction reaction = AbilityReaction.getTypeByValue(aid);
        if (reaction != null) {
            if ((reaction == AbilityReaction.Burning && element != ElementType.Fire) ||
                (reaction == AbilityReaction.Overload && element != ElementType.Fire) ||
                (reaction == AbilityReaction.ElectroCharged && element != ElementType.Electric) ||
                (reaction == AbilityReaction.Superconduct && element != ElementType.Ice) ||
                (reaction == AbilityReaction.Shatter && element != ElementType.None) ||
                (reaction == AbilityReaction.Burgeon && element != ElementType.Grass) ||
                (reaction == AbilityReaction.SwirlPyro && element != ElementType.Fire) ||
                (reaction == AbilityReaction.SwirlHydro && element != ElementType.Water) ||
                (reaction == AbilityReaction.SwirlElectro && element != ElementType.Electric) ||
                (reaction == AbilityReaction.SwirlCryo && element != ElementType.Ice)) {
                return "None";
            }
            return reaction.toString();
        }
        return "None";
    }

    private static String getDefender(Scene scene, int defenseId) {
        if (scene.getEntityById(defenseId) instanceof EntityClientGadget) {
            return getGadgetName(((EntityClientGadget) scene.getEntityById(defenseId)).getGadgetId());
        }
        return getRoot(scene, defenseId);
    }

    public static void registerAvatar(EntityAvatar avatar) {
        avatarMap.put(avatar.getId(), avatar);
    }

    public static final int embryoIndexOffset = 100;
    public static void registerAbilities(EntityAvatar avatar, AbilityControlBlock abilities) {
        abilityMap.put(avatar.getAvatar().getAvatarData().getName(), abilities);
    }

    public static void updateReactionMap(int reactionID, int entityID) {
        reactionMap.put(reactionID, entityID);
        //Log(reactionID + " " + entityID);
    }

    public static void reset() {
        lastTime = 0;
        avatarMap.clear();
        reactionMap.clear();
    }

    public static void parseAttackResult(Scene scene, AttackResult attackResult) {
        int attackerId = attackResult.getAttackerId();
        int casterId = attackResult.getAbilityIdentifier().getAbilityCasterId();
        ElementType element = ElementType.getTypeByValue(attackResult.getElementType());
        int aid = attackResult.getAbilityIdentifier().getInstancedAbilityId();
        int mid = attackResult.getAbilityIdentifier().getInstancedModifierId();
        int defenseId = attackResult.getDefenseId();

        List<String> attackData = Arrays.asList(
            getSource(scene, attackerId, casterId, element, aid, defenseId),
            getAttacker(scene, attackerId, casterId, aid),
            Float.toString(attackResult.getDamage()),
            Boolean.toString(attackResult.getIsCrit()),
            Boolean.toString(attackResult.getElementDurabilityAttenuation() == 1),
            getElementName(element.getValue()),
            getReaction(aid, mid, element, getRoot(scene, attackerId)),
            AmplificationType.getTypeByValue(attackResult.getAmplifyReactionType()).toString(),
            Float.toString(attackResult.getElementAmplifyRate()),
            Integer.toString(attackResult.getAttackCount()),
            Integer.toString(aid),
            Integer.toString(mid),
            getDefender(scene, defenseId)
        );
        log("DAMAGE", attackData);
    }

    public static void logTeamUpdate() {
        List<EntityAvatar> avatarList = new ArrayList<EntityAvatar>(avatarMap.values());
        List<String> teamUpdate = new ArrayList<>(Math.max(
            GAME_OPTIONS.avatarLimits.singlePlayerTeam,
            GAME_OPTIONS.avatarLimits.multiplayerTeam
        ) + 1);
        teamUpdate.add("TEAM");
        for (EntityAvatar avatar : avatarList) {
            teamUpdate.add(avatar.getAvatar().getAvatarData().getName());
        }
        log(String.join(",", teamUpdate));
    }

    public static void log(String type, List<String> dataList) {
        Instant now = Instant.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());
        String time = formatter.format(now);
        String deltaTime = getDeltaTime(now.toEpochMilli());
        List<String> append = Arrays.asList(type, getUID(), time, deltaTime);
        List<String> row = Stream.concat(append.stream(), dataList.stream()).toList();
        log(String.join(",", row));
    }

    public static void log(String text) {
        log.info(text);
    }
}
