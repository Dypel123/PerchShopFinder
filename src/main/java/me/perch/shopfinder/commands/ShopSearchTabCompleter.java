package me.perch.shopfinder.commands;

import me.perch.shopfinder.FindItemAddOn;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.stream.Collectors;

public class ShopSearchTabCompleter implements TabCompleter {
    private final List<String> itemsList;
    private final List<String> enchantBookNames;
    private final List<String> potionEffectNames;
    private static final Set<String> BLOCKED_TAB_ITEMS = Set.of(
            "PETRIFIED_OAK_SLAB", "POTTED_ACACIA_SAPLING", "POTATOES", "CARROTS", "BEETROOTS", "COCOA",
            "BAMBOO_SAPLING", "SWEET_BERRY_BUSH", "PITCHER_CROP", "FROSTED_ICE", "LAVA", "WATER",
            "END_GATEWAY", "BEDROCK", "POTTED_ALLIUM", "POTTED_AZALEA_BUSH", "POTTED_BAMBOO",
            "POTTED_AZURE_BLUET", "POTTED_BIRCH_SAPLING", "POTTED_BLUE_ORCHID", "POTTED_BROWN_MUSHROOM",
            "POTTED_CACTUS", "POTTED_CHERRY_SAPLING", "POTTED_CLOSED_EYEBLOSSOM", "POTTED_CORNFLOWER",
            "POTTED_CRIMSON_FUNGUS", "POTTED_CRIMSON_ROOTS", "POTTED_DANDELION", "POTTED_DARK_OAK_SAPLING",
            "POTTED_DEAD_BUSH", "POTTED_FERN", "POTTED_FLOWERING_AZALEA_BUSH", "POTTED_JUNGLE_SAPLING",
            "POTTED_LILY_OF_THE_VALLEY", "POTTED_MANGROVE_PROPAGULE", "POTTED_OAK_SAPLING",
            "POTTED_OPEN_EYEBLOSSOM", "POTTED_ORANGE_TULIP", "POTTED_OXEYE_DAISY", "POTTED_PALE_OAK_SAPLING",
            "POTTED_PINK_TULIP", "POTTED_POPPY", "POTTED_RED_MUSHROOM", "POTTED_RED_TULIP",
            "POTTED_SPRUCE_SAPLING", "POTTED_TORCHFLOWER", "POTTED_WARPED_FUNGUS", "POTTED_WARPED_ROOTS",
            "POTTED_WHITE_TULIP", "POTTED_WITHER_ROSE", "TUBE_CORAL_WALL_FAN", "FIRE_CORAL_WALL_FAN",
            "HORN_CORAL_WALL_FAN", "BRAIN_CORAL_WALL_FAN", "BUBBLE_CORAL_WALL_FAN", "DEAD_TUBE_CORAL_WALL_FAN",
            "DEAD_FIRE_CORAL_WALL_FAN", "DEAD_HORN_CORAL_WALL_FAN", "DEAD_BRAIN_CORAL_WALL_FAN",
            "DEAD_BUBBLE_CORAL_WALL_FAN", "RED_WALL_BANNER", "LIME_WALL_BANNER", "PINK_WALL_BANNER",
            "GRAY_WALL_BANNER", "CYAN_WALL_BANNER", "BLUE_WALL_BANNER", "WHITE_WALL_BANNER",
            "BROWN_WALL_BANNER", "GREEN_WALL_BANNER", "BLACK_WALL_BANNER", "ORANGE_WALL_BANNER",
            "YELLOW_WALL_BANNER", "PURPLE_WALL_BANNER", "MAGENTA_WALL_BANNER", "LIGHT_BLUE_WALL_BANNER",
            "LIGHT_GRAY_WALL_BANNER", "OAK_WALL_SIGN", "BIRCH_WALL_SIGN", "SPRUCE_WALL_SIGN",
            "ACACIA_WALL_SIGN", "JUNGLE_WALL_SIGN", "WARPED_WALL_SIGN", "DARK_OAK_WALL_SIGN",
            "CRIMSON_WALL_SIGN", "BAMBOO_WALL_SIGN", "MANGROVE_WALL_SIGN", "CHERRY_WALL_SIGN",
            "PALE_OAK_WALL_SIGN", "OAK_WALL_HANGING_SIGN", "BIRCH_WALL_HANGING_SIGN", "SPRUCE_WALL_HANGING_SIGN",
            "ACACIA_WALL_HANGING_SIGN", "JUNGLE_WALL_HANGING_SIGN", "WARPED_WALL_HANGING_SIGN",
            "DARK_OAK_WALL_HANGING_SIGN", "CRIMSON_WALL_HANGING_SIGN", "BAMBOO_WALL_HANGING_SIGN",
            "MANGROVE_WALL_HANGING_SIGN", "CHERRY_WALL_HANGING_SIGN", "PALE_OAK_WALL_HANGING_SIGN",
            "PISTON_HEAD", "MOVING_PISTON", "TRIPWIRE", "REDSTONE_WIRE", "WALL_TORCH", "REDSTONE_WALL_TORCH",
            "SOUL_WALL_TORCH", "SKELETON_WALL_SKULL", "WITHER_SKELETON_WALL_SKULL", "ZOMBIE_WALL_HEAD",
            "CREEPER_WALL_HEAD", "PLAYER_WALL_HEAD", "PIGLIN_WALL_HEAD", "DRAGON_WALL_HEAD", "VOID_AIR",
            "AIR", "CAVE_AIR", "SPAWNER", "BUBBLE_COLUMN", "KNOWLEDGE_BOOK", "FIRE", "SOUL_FIRE",
            "RED_CANDLE_CAKE", "LIME_CANDLE_CAKE", "PINK_CANDLE_CAKE", "GRAY_CANDLE_CAKE", "CYAN_CANDLE_CAKE",
            "BLUE_CANDLE_CAKE", "WHITE_CANDLE_CAKE", "BROWN_CANDLE_CAKE", "GREEN_CANDLE_CAKE",
            "BLACK_CANDLE_CAKE", "ORANGE_CANDLE_CAKE", "YELLOW_CANDLE_CAKE", "PURPLE_CANDLE_CAKE",
            "MAGENTA_CANDLE_CAKE", "LIGHT_BLUE_CANDLE_CAKE", "LIGHT_GRAY_CANDLE_CAKE", "FARMLAND",
            "DIRT_PATH", "ATTACHED_MELON_STEM", "ATTACHED_PUMPKIN_STEM", "INFESTED_STONE",
            "INFESTED_COBBLESTONE", "INFESTED_STONE_BRICKS", "INFESTED_MOSSY_STONE_BRICKS",
            "INFESTED_CRACKED_STONE_BRICKS", "INFESTED_CHISELED_STONE_BRICKS", "INFESTED_DEEPSLATE",
            "NETHER_PORTAL", "END_PORTAL", "END_PORTAL_FRAME", "WATER_CAULDRON", "LAVA_CAULDRON",
            "POWDER_SNOW_CAULDRON", "BIG_DRIPLEAF_STEM", "KELP_PLANT", "CAVE_VINES", "CAVE_VINES_PLANT",
            "TWISTING_VINES_PLANT", "WEEPING_VINES_PLANT"
    );

    private static final Map<String, String> FRIENDLY_POTION_EFFECT_NAMES = buildFriendlyPotionEffectNameMap();

    private static Map<String, String> buildFriendlyPotionEffectNameMap() {
        Map<String, String> map = new HashMap<>();
        map.put("HASTE", "FAST_DIGGING");
        map.put("MININGFATIGUE", "SLOW_DIGGING");
        map.put("STRENGTH", "INCREASE_DAMAGE");
        map.put("INSTANTHEALTH", "HEAL");
        map.put("HEAL", "HEAL");
        map.put("INSTANTHARM", "HARM");
        map.put("HARM", "HARM");
        map.put("JUMPBOOST", "JUMP");
        map.put("JUMP", "JUMP");
        map.put("REGENERATION", "REGENERATION");
        map.put("REGEN", "REGENERATION");
        map.put("RESISTANCE", "DAMAGE_RESISTANCE");
        map.put("DAMAGERESISTANCE", "DAMAGE_RESISTANCE");
        map.put("FIRERESISTANCE", "FIRE_RESISTANCE");
        map.put("WATERBREATHING", "WATER_BREATHING");
        map.put("NIGHTVISION", "NIGHT_VISION");
        map.put("SLOWFALLING", "SLOW_FALLING");
        map.put("BADOMEN", "BAD_OMEN");
        map.put("HEROOFTHEVILLAGE", "HERO_OF_THE_VILLAGE");
        map.put("CONDUITPOWER", "CONDUIT_POWER");
        map.put("DOLPHINSGRACE", "DOLPHINS_GRACE");
        map.put("LUCK", "LUCK");
        map.put("BADLUCK", "UNLUCK");
        map.put("ABSORPTION", "ABSORPTION");
        map.put("INVISIBILITY", "INVISIBILITY");
        map.put("POISON", "POISON");
        map.put("SLOWNESS", "SLOW");
        map.put("SLOW", "SLOW");
        map.put("SPEED", "SPEED");
        map.put("WEAKNESS", "WEAKNESS");
        map.put("WITHER", "WITHER");
        map.put("LEVITATION", "LEVITATION");
        map.put("GLOW", "GLOWING");
        map.put("BLINDNESS", "BLINDNESS");
        // 1.21+ and aliases
        map.put("TURTLEMASTER", "TURTLE_MASTER");
        map.put("NAUSEA", "CONFUSION");
        map.put("CONFUSION", "CONFUSION");
        map.put("OOZING", "OOZING");
        map.put("INFESTATION", "INFESTED");
        map.put("WEAVING", "WEAVING");
        map.put("WINDCHARGING", "WIND_CHARGED");

        // Added Missing Aliases
        map.put("HARMING", "HARM");
        map.put("LEAPING", "JUMP");
        map.put("HEALING", "HEAL");
        map.put("DECAY", "WITHER");
        map.put("DULLNESS", "SLOW_DIGGING");

        return map;
    }

    public ShopSearchTabCompleter() {
        itemsList = new ArrayList<>();
        for (Material mat : Material.values()) {
            if (!FindItemAddOn.getConfigProvider().getBlacklistedMaterials().contains(mat)) {
                itemsList.add(mat.name());
            }
        }
        enchantBookNames = buildEnchantmentBookNames();
        potionEffectNames = buildPotionEffectNames();
    }

    private List<String> buildEnchantmentBookNames() {
        Map<String, String> special = new HashMap<>();
        special.put("PROTECTION", "PROTECTION");
        special.put("FIRE_PROTECTION", "FIREPROTECTION");
        special.put("FEATHER_FALLING", "FEATHERFALLING");
        special.put("BLAST_PROTECTION", "BLASTPROTECTION");
        special.put("PROJECTILE_PROTECTION", "PROJECTILEPROTECTION");
        special.put("RESPIRATION", "RESPIRATION");
        special.put("AQUA_AFFINITY", "AQUAAFFINITY");
        special.put("THORNS", "THORNS");
        special.put("DEPTH_STRIDER", "DEPTHSTRIDER");
        special.put("FROST_WALKER", "FROSTWALKER");
        special.put("BINDING_CURSE", "CURSEOFBINDING");
        special.put("SOUL_SPEED", "SOULSPEED");
        special.put("SWIFT_SNEAK", "SWIFTSNEAK");
        special.put("SHARPNESS", "SHARPNESS");
        special.put("SMITE", "SMITE");
        special.put("BANE_OF_ARTHROPODS", "BANEOFARTHROPODS");
        special.put("KNOCKBACK", "KNOCKBACK");
        special.put("FIRE_ASPECT", "FIREASPECT");
        special.put("LOOTING", "LOOTING");
        special.put("SWEEPING_EDGE", "SWEEPINGEDGE");
        special.put("EFFICIENCY", "EFFICIENCY");
        special.put("SILK_TOUCH", "SILKTOUCH");
        special.put("UNBREAKING", "UNBREAKING");
        special.put("FORTUNE", "FORTUNE");
        special.put("POWER", "POWER");
        special.put("PUNCH", "PUNCH");
        special.put("FLAME", "FLAME");
        special.put("INFINITY", "INFINITY");
        special.put("LUCK_OF_THE_SEA", "LUCKOFTHESEA");
        special.put("LURE", "LURE");
        special.put("LOYALTY", "LOYALTY");
        special.put("IMPALING", "IMPALING");
        special.put("RIPTIDE", "RIPTIDE");
        special.put("CHANNELING", "CHANNELING");
        special.put("MULTISHOT", "MULTISHOT");
        special.put("PIERCING", "PIERCING");
        special.put("QUICK_CHARGE", "QUICKCHARGE");
        special.put("MENDING", "MENDING");
        special.put("VANISHING_CURSE", "CURSEOFVANISHING");

        Set<String> names = new LinkedHashSet<>();
        for (Enchantment ench : Enchantment.values()) {
            String key = ench.getKey().getKey().toUpperCase();
            String bookName = special.getOrDefault(key, key.replace("_", ""));
            names.add(bookName);
        }
        return new ArrayList<>(names);
    }

    private List<String> buildPotionEffectNames() {
        Set<String> names = new LinkedHashSet<>();
        names.addAll(FRIENDLY_POTION_EFFECT_NAMES.keySet());
        for (PotionEffectType effect : PotionEffectType.values()) {
            if (effect == null) continue;
            String effectName = effect.getName().toUpperCase().replace("_", "").replace(" ", "");
            names.add(effectName);
        }
        try {
            Class<?> potionTypeClass = Class.forName("org.bukkit.potion.PotionType");
            for (Object type : (Object[]) potionTypeClass.getMethod("values").invoke(null)) {
                String typeName = type.toString().toUpperCase().replace("_", "");
                names.add(typeName);
            }
        } catch (Exception ignored) {
        }
        return new ArrayList<>(names);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) return Collections.emptyList();

        String toComplete = args[args.length - 1].toLowerCase();
        List<String> completions = new ArrayList<>();

        // Custom completions
        if ("inv".startsWith(toComplete)) completions.add("inv");
        if ("hand".startsWith(toComplete)) completions.add("hand");
        if ("safarinet".startsWith(toComplete)) completions.add("safarinet");
        if ("voucher".startsWith(toComplete)) completions.add("voucher");
        if ("key".startsWith(toComplete)) completions.add("key");
        if ("unbreakable".startsWith(toComplete)) completions.add("unbreakable");
        if ("claimblocks".startsWith(toComplete)) completions.add("claimblocks");
        if ("playtime".startsWith(toComplete)) completions.add("playtime");
        if ("tags".startsWith(toComplete)) completions.add("tags");
        if ("tag".startsWith(toComplete)) completions.add("tag");
        if ("tracker".startsWith(toComplete)) completions.add("tracker");
        if ("art".startsWith(toComplete)) completions.add("art");
        if ("artmap".startsWith(toComplete)) completions.add("artmap");
        if ("mapart".startsWith(toComplete)) completions.add("mapart");
        if ("banner".startsWith(toComplete)) completions.add("banner");
        if ("mob_egg".startsWith(toComplete)) completions.add("mob_egg");
        if ("bottle_o'_enchanting".startsWith(toComplete)) completions.add("BOTTLE_O'_ENCHANTING");

        // Aliases for Writable Book
        if ("book_and_quill".startsWith(toComplete)) completions.add("book_and_quill");
        if ("bookandquill".startsWith(toComplete)) completions.add("bookandquill");

        completions.addAll(enchantBookNames.stream()
                .filter(e -> e.toLowerCase().startsWith(toComplete))
                .collect(Collectors.toList()));

        completions.addAll(potionEffectNames.stream()
                .filter(e -> e.toLowerCase().startsWith(toComplete))
                .collect(Collectors.toList()));

        completions.addAll(itemsList.stream()
                .filter(item -> item.toLowerCase().startsWith(toComplete))
                .filter(item -> !BLOCKED_TAB_ITEMS.contains(item.toUpperCase()))
                .filter(item -> !completions.contains(item))
                .collect(Collectors.toList()));

        return completions;
    }
}