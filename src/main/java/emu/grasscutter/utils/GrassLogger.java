package emu.grasscutter.utils;

import ch.qos.logback.classic.Logger;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.data.excels.MonsterData;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityClientGadget;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.props.*;
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
    private static final Int2ObjectMap<String> avatarNameMap = new Int2ObjectLinkedOpenHashMap<>();
    private static final Map<String, AbilityControlBlock> abilityMap = new HashMap<>();
    private static final Int2IntMap reactionMap = new Int2IntOpenHashMap();
    private static final Int2IntMap gadgetOwnerMap = new Int2IntOpenHashMap();
    private static final Int2IntMap gadgetIdMap = new Int2IntOpenHashMap();
    private static final Long2ObjectMap<MonsterAffix> monsterAffixMap = new Long2ObjectOpenHashMap<>();
    private static final Int2ObjectMap<String> monsterNameMap = new Int2ObjectOpenHashMap<>();

    private static String getUID() {
        return Integer.toString(++uidCount);
    }

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

    private static EntityType getEntityType(int id) {
        return EntityType.getTypeByValue(id >> 24);
    }

    private static String getAvatarName(int id) {
        return avatarNameMap.get(id);
    }

    private static class MonsterAffix {
        private static int letterCount = 0;
        public String letter;
        public int count;

        public MonsterAffix() {
            int n = letterCount++;
            char[] b26 = Integer.toString(n, 26).toCharArray();
            for (int i = 0; i < b26.length; i++) {
                // original offsets are 10 and 49, subtracted by 32 to get UPPERCASE chars
                b26[i] += b26[i] > '9' ? -22 : 17;
            }
            this.letter = new String(b26);
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

    private static String getMonsterName(int id) {
        return monsterNameMap.get(id);
    }

    private static String getAbilityName(String avatarName, int aid) {
        AbilityControlBlock abilities = abilityMap.get(avatarName);
        int index = aid - embryoIndexOffset - 1;
        if (index < abilities.getAbilityEmbryoListCount()) {
            AbilityEmbryo embryo = abilities.getAbilityEmbryoList(index);
            int hash = embryo.getAbilityNameHash();
            String name = GameData.getAbilityHashes().get(hash);
            if (name != null) return name;
            return "UA-" + hash;
        }
        return "AID out of bounds"; //should never happen
    }

    private static String getGadgetName(int id) {
        int gadgetId = gadgetIdMap.get(id);
        GadgetData gadgetData = GameData.getGadgetDataMap().get(gadgetId);
        if (gadgetData != null) {
            String name = gadgetData.getJsonName();
            if (name != null) return name;
        }
        return "UG-" + gadgetId;
    }

    private static String getRoot(int id) {
        EntityType type = getEntityType(id);
        switch (type) {
            case Avatar -> { return getAvatarName(id); }
            case Foundation -> { return getRoot(gadgetOwnerMap.get(id)); }
            case Monster -> { return getMonsterName(id); }
            case Level, MPLevel, Team -> { return type.toString(); }
            default -> { return Integer.toString(id); }
        }
    }

    private static String getAttacker(int attackerId, int casterId, int aid) {
        if (getEntityType(attackerId) == EntityType.Gadget || getEntityType(casterId) == EntityType.Gadget) {
            String abilityReaction = AbilityReaction.getTypeByValue(aid).toString();
            int baseReaction = BaseReaction.getTypeByName(abilityReaction).getValue();
            int sourceId = reactionMap.getOrDefault(baseReaction, -1);
            return sourceId != -1 ? getRoot(sourceId) : "Unknown";
        }
        return getRoot(attackerId);
    }

    private static String getSource(int attackerId, int casterId, ElementType element, int aid, int defenseId) {
        EntityType type = getEntityType(attackerId);
        if (type == EntityType.Gadget || getEntityType(casterId) == EntityType.Gadget)
            return "Reaction";
        if (attackerId == defenseId) {
            if (element == ElementType.None)
                return "Fall Damage";
            return "Self-Inflicted";
        }

        switch (type) {
            case Avatar -> {
                if (aid > embryoIndexOffset)
                    return getAbilityName(getAvatarName(attackerId), aid);
                return "Direct";
            }
            case Foundation -> { return getGadgetName(attackerId); }
            case Level -> { return "Environment"; }
            default -> { return type.toString(); }
        }
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

    private static String getDefender(int defenseId) {
        if (getEntityType(defenseId) == EntityType.Foundation)
            return getGadgetName(defenseId);
        return getRoot(defenseId);
    }

    public static void registerAvatar(EntityAvatar avatar) {
        avatarNameMap.put(avatar.getId(), avatar.getAvatar().getAvatarData().getName());
    }

    public static final int embryoIndexOffset = 100;
    public static void registerAbilities(EntityAvatar avatar, AbilityControlBlock abilities) {
        abilityMap.put(avatar.getAvatar().getAvatarData().getName(), abilities);
    }

    public static void registerGadget(EntityClientGadget gadget) {
        int id = gadget.getId();
        gadgetOwnerMap.put(id, gadget.getOriginalOwnerEntityId());
        gadgetIdMap.put(id, gadget.getGadgetId());
    }

    public static void registerMonster(EntityMonster monster) {
        String name;
        MonsterData monsterData = monster.getMonsterData();
        if (monsterData != null) {
            long hash = monsterData.getDescribeData().getNameTextMapHash();
            String affix = getMonsterAffix(hash);
            name = affix + " " + monsterData.getMonsterName(); //preferably this should take from the name hash, but fuck TextMaps
        } else
            name = getMonsterAffix(-1) + " Unknown";
        monsterNameMap.put(monster.getId(), name);
    }

    public static void updateReactionMap(int reactionID, int entityID) {
        reactionMap.put(reactionID, entityID);
    }

    public static void reset() {
        lastTime = 0;
        avatarNameMap.clear();
        reactionMap.clear();
    }

    public static void parseAttackResult(AttackResult attackResult) {
        int attackerId = attackResult.getAttackerId();
        int casterId = attackResult.getAbilityIdentifier().getAbilityCasterId();
        ElementType element = ElementType.getTypeByValue(attackResult.getElementType());
        int aid = attackResult.getAbilityIdentifier().getInstancedAbilityId();
        int mid = attackResult.getAbilityIdentifier().getInstancedModifierId();
        int defenseId = attackResult.getDefenseId();

        List<String> attackData = Arrays.asList(
            getSource(attackerId, casterId, element, aid, defenseId),
            getAttacker(attackerId, casterId, aid),
            Float.toString(attackResult.getDamage()),
            Boolean.toString(attackResult.getIsCrit()),
            Boolean.toString(attackResult.getElementDurabilityAttenuation() == 1),
            getElementName(element.getValue()),
            getReaction(aid, mid, element, getRoot(attackerId)),
            AmplificationType.getTypeByValue(attackResult.getAmplifyReactionType()).toString(),
            Float.toString(attackResult.getElementAmplifyRate()),
            Integer.toString(attackResult.getAttackCount()),
            Integer.toString(aid),
            Integer.toString(mid),
            getDefender(defenseId)
        );
        log("DAMAGE", attackData);
    }

    public static void logTeamUpdate() {
        List<String> avatarList = new ArrayList<>(avatarNameMap.values());
        avatarList.add(0, "TEAM");
        log(String.join(",", avatarList));
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
