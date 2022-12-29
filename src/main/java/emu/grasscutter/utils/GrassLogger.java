package emu.grasscutter.utils;

import ch.qos.logback.classic.Logger;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.AvatarSkillData;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.data.excels.MonsterData;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityClientGadget;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.*;
import emu.grasscutter.net.proto.AbilityControlBlockOuterClass.AbilityControlBlock;
import emu.grasscutter.net.proto.AbilityEmbryoOuterClass.AbilityEmbryo;
import emu.grasscutter.net.proto.AttackResultOuterClass.AttackResult;

import it.unimi.dsi.fastutil.ints.*;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

public class GrassLogger {
    private static final List<String> hyperbloomExceptions = List.of("Collei", "Alhatham");
    private static final Map<AbilityReaction, ElementType> reactionElements = Map.of(
        AbilityReaction.Burning, ElementType.Fire,
        AbilityReaction.Overload, ElementType.Fire,
        AbilityReaction.ElectroCharged, ElementType.Electric,
        AbilityReaction.Superconduct, ElementType.Ice,
        AbilityReaction.Shatter, ElementType.None,
        AbilityReaction.Burgeon, ElementType.Grass,
        AbilityReaction.SwirlPyro, ElementType.Fire,
        AbilityReaction.SwirlHydro, ElementType.Water,
        AbilityReaction.SwirlElectro, ElementType.Electric,
        AbilityReaction.SwirlCryo, ElementType.Ice
    );
    private static final String[] elementNames = {
        "Physical", "Pyro", "Hydro", "Dendro", "Electro", "Cryo",
        "Frozen", "Anemo", "Geo", "AntiFire", "VehicleMuteIce",
        "Mushroom", "Overdose", "Wood", "COUNT"
    };
    
    private static int uidCount = 0;
    private static long lastTime = 0;

    private static final Int2ObjectMap<String> avatarNameMap = new Int2ObjectLinkedOpenHashMap<>();
    private static final Map<String, AbilityControlBlock> abilityMap = new HashMap<>();
    private static final Int2IntMap reactionMap = new Int2IntOpenHashMap();

    private static final Int2IntMap gadgetOwnerMap = new Int2IntOpenHashMap();
    private static final Int2IntMap gadgetIdMap = new Int2IntOpenHashMap();
    
    private static final Int2ObjectMap<MonsterAffix> monsterAffixMap = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectMap<String> monsterNameMap = new Int2ObjectOpenHashMap<>();

    private static final Logger grassLogger = (Logger) LoggerFactory.getLogger(GrassLogger.class);

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

    private static String getMonsterAffix(int id) {
        MonsterAffix affix;
        if (monsterAffixMap.containsKey(id)) {
            affix = monsterAffixMap.get(id);
            affix.count++;
        } else {
            affix = new MonsterAffix();
            monsterAffixMap.put(id, affix);
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

    private static String getSkillName(int skillId) {
        AvatarSkillData skill = GameData.getAvatarSkillDataMap().get(skillId);
        if (skill != null) {
            String abilityName = skill.getAbilityName();
            if (!abilityName.isEmpty()) return abilityName;
            String skillIcon = skill.getSkillIcon();
            if (!skillIcon.isEmpty()) return skillIcon;
        }
        return Integer.toString(skillId);
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
            if (aid == 2) {
                if (mid == 5) return "Bloom";
                else if (mid == 4) return "BountifulBloom";
            } else if (aid == 1 && mid == 2) {
                if (!hyperbloomExceptions.contains(attacker))
                    return "Hyperbloom";
            }
        }

        AbilityReaction reaction = AbilityReaction.getTypeByValue(aid);
        if (reaction != null && element == reactionElements.get(reaction)) 
            return reaction.toString();
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
        String affix, name;
        MonsterData monsterData = monster.getMonsterData();
        if (monsterData != null) {
            affix = getMonsterAffix(monsterData.getId());
            name = monsterData.getMonsterName();
        } else {
            affix = getMonsterAffix(-1);
            name = "Unknown";
        }
        name = affix + " " + name;
        monsterNameMap.put(monster.getId(), name);
        Player player = monster.getScene().getPlayers().get(0); //singleplayer only
        player.getServer().getChatSystem().sendPrivateMessageFromServer(player.getUid(), "Spawned " + affix);
    }

    public static void updateReactionMap(int reactionID, int entityID) {
        reactionMap.put(reactionID, entityID);
    }

    public static void reset() {
        lastTime = 0;
        avatarNameMap.clear();
        reactionMap.clear();
    }

    public static void logAttackResult(AttackResult attackResult) {
        int attackerId = attackResult.getAttackerId();
        int casterId = attackResult.getAbilityIdentifier().getAbilityCasterId();
        ElementType element = ElementType.getTypeByValue(attackResult.getElementType());
        int aid = attackResult.getAbilityIdentifier().getInstancedAbilityId();
        int mid = attackResult.getAbilityIdentifier().getInstancedModifierId();
        int defenseId = attackResult.getDefenseId();

        List<String> attackData = List.of(
            getSource(attackerId, casterId, element, aid, defenseId),
            getAttacker(attackerId, casterId, aid),
            Float.toString(attackResult.getDamage()),
            Boolean.toString(attackResult.getIsCrit()),
            Float.toString(attackResult.getElementDurabilityAttenuation()),
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

    public static void logSkillCast(int skillId, int casterId) {
        List<String> skillCastData = List.of(
            getSkillName(skillId),
            getRoot(casterId)
        );
        //log("SKILL", skillCastData);
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
        List<String> append = List.of(type, getUID(), time, deltaTime);
        List<String> row = Stream.concat(append.stream(), dataList.stream()).toList();
        log(String.join(",", row));
    }

    public static void log(String text) {
        grassLogger.info(text);
    }
}