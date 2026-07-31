package me.perch.shopfinder.commands;

import me.perch.shopfinder.FindItemAddOn;
import me.perch.shopfinder.handlers.command.CmdExecutorHandler;
import me.perch.shopfinder.models.FoundShopItemModel;
import me.kodysimpson.simpapi.colors.ColorTranslator;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import me.perch.shopfinder.utils.CustomItemMatchers;

import java.util.*;
import java.util.stream.Collectors;

public class WhereToBuyCommand implements CommandExecutor {

    private final CmdExecutorHandler cmdExecutor;
    private final String buyCommand;

    private static final Map<String, PotionEffectType> FRIENDLY_POTION_EFFECTS = buildFriendlyPotionEffectMap();
    private static final Map<String, Enchantment> BOOK_NAME_TO_ENCHANTMENT = buildBookNameToEnchantmentMap();

    private static Map<String, PotionEffectType> buildFriendlyPotionEffectMap() {
        Map<String, PotionEffectType> map = new HashMap<>();
        map.put("HASTE", PotionEffectType.getByName("FAST_DIGGING"));
        map.put("MININGFATIGUE", PotionEffectType.getByName("SLOW_DIGGING"));
        map.put("STRENGTH", PotionEffectType.getByName("INCREASE_DAMAGE"));
        map.put("INSTANTHEALTH", PotionEffectType.getByName("HEAL"));
        map.put("INSTANTHARM", PotionEffectType.getByName("HARM"));
        map.put("JUMPBOOST", PotionEffectType.getByName("JUMP"));
        map.put("JUMP", PotionEffectType.getByName("JUMP"));
        map.put("REGENERATION", PotionEffectType.getByName("REGENERATION"));
        map.put("RESISTANCE", PotionEffectType.getByName("DAMAGE_RESISTANCE"));
        map.put("FIRERESISTANCE", PotionEffectType.getByName("FIRE_RESISTANCE"));
        map.put("WATERBREATHING", PotionEffectType.getByName("WATER_BREATHING"));
        map.put("NIGHTVISION", PotionEffectType.getByName("NIGHT_VISION"));
        map.put("SLOWFALLING", PotionEffectType.getByName("SLOW_FALLING"));
        map.put("BADOMEN", PotionEffectType.getByName("BAD_OMEN"));
        map.put("HEROOFTHEVILLAGE", PotionEffectType.getByName("HERO_OF_THE_VILLAGE"));
        map.put("CONDUITPOWER", PotionEffectType.getByName("CONDUIT_POWER"));
        map.put("DOLPHINSGRACE", PotionEffectType.getByName("DOLPHINS_GRACE"));
        map.put("LUCK", PotionEffectType.getByName("LUCK"));
        map.put("BADLUCK", PotionEffectType.getByName("UNLUCK"));
        map.put("ABSORPTION", PotionEffectType.getByName("ABSORPTION"));
        map.put("INVISIBILITY", PotionEffectType.getByName("INVISIBILITY"));
        map.put("POISON", PotionEffectType.getByName("POISON"));
        map.put("SLOWNESS", PotionEffectType.getByName("SLOW"));
        map.put("SWIFTNESS", PotionEffectType.getByName("SPEED"));
        map.put("WEAKNESS", PotionEffectType.getByName("WEAKNESS"));
        map.put("WITHER", PotionEffectType.getByName("WITHER"));
        map.put("LEVITATION", PotionEffectType.getByName("LEVITATION"));
        map.put("GLOW", PotionEffectType.getByName("GLOWING"));
        map.put("BLINDNESS", PotionEffectType.getByName("BLINDNESS"));
        map.put("OOZING", PotionEffectType.getByName("OOZING"));
        map.put("INFESTATION", PotionEffectType.getByName("INFESTED"));
        map.put("WEAVING", PotionEffectType.getByName("WEAVING"));
        map.put("WINDCHARGING", PotionEffectType.getByName("WIND_CHARGED"));
        map.put("NAUSEA", PotionEffectType.getByName("CONFUSION"));
        map.put("CONFUSION", PotionEffectType.getByName("CONFUSION"));

        // Missing Aliases Fixed Below:
        map.put("HARMING", PotionEffectType.getByName("HARM"));
        map.put("LEAPING", PotionEffectType.getByName("JUMP"));
        map.put("REGEN", PotionEffectType.getByName("REGENERATION"));
        map.put("HEALING", PotionEffectType.getByName("HEAL"));
        map.put("DECAY", PotionEffectType.getByName("WITHER"));
        map.put("DULLNESS", PotionEffectType.getByName("SLOW_DIGGING"));

        // Notice: TURTLEMASTER was removed from here to be handled explicitly!

        return map;
    }

    private static Map<String, Enchantment> buildBookNameToEnchantmentMap() {
        Map<String, String> special = new HashMap<>();
        special.put("BINDING_CURSE", "CURSEOFBINDING");
        special.put("VANISHING_CURSE", "CURSEOFVANISHING");
        Map<String, Enchantment> map = new HashMap<>();
        for (Enchantment ench : Enchantment.values()) {
            String key = ench.getKey().getKey().toUpperCase();
            String bookName = special.getOrDefault(key, key.replace("_", ""));
            map.put(bookName.toLowerCase(), ench);
            if (bookName.equals("CURSEOFBINDING")) map.put("bindingcurse", ench);
            if (bookName.equals("CURSEOFVANISHING")) map.put("vanishingcurse", ench);
        }
        return map;
    }

    private static String detectArtAlias(String[] args) {
        if (args == null || args.length == 0) return null;
        String a0 = args[0].toLowerCase(Locale.ROOT);
        if (a0.equals("art")) return "wtb_art";
        if (a0.equals("artmap")) return "wtb_artmap";
        if (a0.equals("mapart")) return "wtb_mapart";
        if (a0.equals("playtime")) return "wtb_playtime";
        if (a0.equals("claimblocks")) return "wtb_claimblocks";
        return null;
    }

    private static String[] remapArtAliases(String[] args) {
        if (args == null || args.length == 0) return args;
        String a0 = args[0].toLowerCase(Locale.ROOT);
        switch (a0) {
            case "art":
                return new String[] { "lore:copyright", "lore:artwork" };
            case "playtime":
                return new String[] { "lore:playtime" };
            case "artmap":
                return new String[] { "lore:artwork" };
            case "mapart":
                return new String[] { "lore:copyright" };
            case "claimblocks":
                return new String[] { "lore:claims" };
            default:
                return args;
        }
    }

    public WhereToBuyCommand() {
        this.cmdExecutor = new CmdExecutorHandler();
        if (StringUtils.isEmpty(FindItemAddOn.getConfigProvider().FIND_ITEM_TO_BUY_AUTOCOMPLETE)
                || StringUtils.containsIgnoreCase(FindItemAddOn.getConfigProvider().FIND_ITEM_TO_BUY_AUTOCOMPLETE, " ")) {
            this.buyCommand = "TO_BUY";
        } else {
            this.buyCommand = FindItemAddOn.getConfigProvider().FIND_ITEM_TO_BUY_AUTOCOMPLETE;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = sender instanceof Player ? (Player) sender : null;
        if (player == null) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }

        if (args.length == 0) {
            Bukkit.getScheduler().runTask(JavaPlugin.getProvidingPlugin(getClass()), () -> {
                player.sendMessage(ColorTranslator.translateColorCodes("&cInvalid item, redirecting to menu."));
                player.performCommand("wtbmenu");
            });
            return true;
        }

        String firstArg = args[0];

        if (firstArg.equalsIgnoreCase("inv")) {
            cmdExecutor.handleShopSearchForInventory(buyCommand, player);
            return true;
        }

        if (firstArg.equalsIgnoreCase("hand")) {
            Material handMat = player.getInventory().getItemInMainHand().getType();
            if (handMat == Material.AIR) {
                sender.sendMessage(ColorTranslator.translateColorCodes(
                        FindItemAddOn.getConfigProvider().PLUGIN_PREFIX + "&cYou are not holding any item!"));
                return true;
            }
            args[0] = handMat.name();
        }

        if (firstArg.equalsIgnoreCase("unbreakable")) {
            cmdExecutor.handleShopSearchForUnbreakable(buyCommand, sender);
            return true;
        }

        String aliasOrigin = detectArtAlias(args);
        args = remapArtAliases(args);
        String[] searchArgs = args.clone();

        boolean isBuying = buyCommand.equalsIgnoreCase("TO_BUY") ||
                buyCommand.equalsIgnoreCase(FindItemAddOn.getConfigProvider().FIND_ITEM_TO_BUY_AUTOCOMPLETE);

        Bukkit.getScheduler().runTaskAsynchronously(JavaPlugin.getProvidingPlugin(getClass()), () -> {
            ShopSearchResult result = new ShopSearchResult();
            if (aliasOrigin != null) result.originCommand = aliasOrigin;

            for (String singleItem : searchArgs) {
                singleItem = singleItem.trim();
                if (singleItem.isEmpty()) continue;

                if (singleItem.equalsIgnoreCase("voucher")
                        || singleItem.equalsIgnoreCase("vouchers")) {
                    result.anyValid = true;

                    result.allResults.addAll(
                            FindItemAddOn.getQsApiInstance()
                                    .findItemsMatchingFromAllShops(
                                            CustomItemMatchers::isVoucher,
                                            isBuying,
                                            player
                                    )
                    );

                    continue;
                }

                if (singleItem.equalsIgnoreCase("tracker")
                        || singleItem.equalsIgnoreCase("trackers")) {
                    result.anyValid = true;

                    result.allResults.addAll(
                            FindItemAddOn.getQsApiInstance()
                                    .findItemsMatchingFromAllShops(
                                            CustomItemMatchers::isTracker,
                                            isBuying,
                                            player
                                    )
                    );

                    continue;
                }

                if (singleItem.equalsIgnoreCase("mob_egg")) {
                    result.anyValid = true;
                    Material brownEgg = Material.getMaterial("BROWN_EGG");
                    if (brownEgg != null) {
                        List<FoundShopItemModel> eggItems = (List<FoundShopItemModel>) FindItemAddOn.getQsApiInstance()
                                .findItemBasedOnTypeFromAllShops(new ItemStack(brownEgg), isBuying, player);

                        List<FoundShopItemModel> caughtMobs = eggItems.stream()
                                .filter(shopItem -> {
                                    ItemStack item = shopItem.getItemStack();
                                    if (item == null || !item.hasItemMeta()) return false;

                                    if (item.getItemMeta().hasLore()) {
                                        List<String> lore = item.getItemMeta().getLore();
                                        if (lore != null) {
                                            for (String line : lore) {
                                                String lowerLine = line.toLowerCase(Locale.ROOT);
                                                if (lowerLine.contains("contains") || lowerLine.contains("catch")) {
                                                    return true;
                                                }
                                            }
                                        }
                                    }
                                    return false;
                                })
                                .collect(Collectors.toList());
                        result.allResults.addAll(caughtMobs);
                    }
                    continue;
                }

                if (singleItem.toLowerCase(Locale.ROOT).startsWith("lore:")) {
                    result.anyValid = true;
                    String loreSearch = singleItem.substring(5);

                    List<FoundShopItemModel> loreMatches =
                            FindItemAddOn.getQsApiInstance()
                                    .findItemsMatchingFromAllShops(
                                            item -> CustomItemMatchers.loreContains(
                                                    item,
                                                    loreSearch
                                            ),
                                            isBuying,
                                            player
                                    );

                    result.allResults.addAll(loreMatches);
                    continue;
                }

                Enchantment enchantment = getEnchantmentByName(singleItem);
                if (enchantment != null) {
                    result.anyValid = true;
                    List<FoundShopItemModel> books =
                            FindItemAddOn.getQsApiInstance()
                                    .findItemsMatchingFromAllShops(
                                            item -> CustomItemMatchers
                                                    .hasStoredEnchantment(
                                                            item,
                                                            enchantment
                                                    ),
                                            isBuying,
                                            player
                                    );

                    result.allResults.addAll(books);
                    continue;
                }

                PotionEffectType effect = getPotionEffectByName(singleItem);
                boolean isTurtleMaster =
                        singleItem.equalsIgnoreCase("TURTLEMASTER");

                if (effect != null || isTurtleMaster) {
                    result.anyValid = true;

                    List<FoundShopItemModel> potions =
                            FindItemAddOn.getQsApiInstance()
                                    .findItemsMatchingFromAllShops(
                                            item -> CustomItemMatchers.hasPotionEffect(
                                                    item,
                                                    effect,
                                                    isTurtleMaster
                                            ),
                                            isBuying,
                                            player
                                    );

                    result.allResults.addAll(potions);
                    continue;
                }

                if (singleItem.equalsIgnoreCase("tags")
                        || singleItem.equalsIgnoreCase("tag")) {
                    result.anyValid = true;

                    result.allResults.addAll(
                            FindItemAddOn.getQsApiInstance()
                                    .findItemsMatchingFromAllShops(
                                            CustomItemMatchers::isTagItem,
                                            isBuying,
                                            player
                                    )
                    );

                    result.originCommand = "wtb_tags";
                    continue;
                }

                if (singleItem.equalsIgnoreCase("shulker_box")) {
                    result.anyValid = true;

                    result.allResults.addAll(
                            FindItemAddOn.getQsApiInstance()
                                    .findItemsMatchingFromAllShops(
                                            CustomItemMatchers::isShulkerBox,
                                            isBuying,
                                            player
                                    )
                    );

                    continue;
                }

                if (singleItem.equalsIgnoreCase("banner")) {
                    result.anyValid = true;

                    List<Material> bannerVariants = Arrays.stream(Material.values())
                            .filter(Material::isItem)
                            .filter(m -> {
                                String n = m.name();
                                return n.endsWith("_BANNER") && !n.endsWith("_WALL_BANNER");
                            })
                            .collect(Collectors.toList());

                    for (Material variant : bannerVariants) {
                        List<FoundShopItemModel> variantMatches =
                                (List<FoundShopItemModel>) FindItemAddOn.getQsApiInstance()
                                        .findItemBasedOnTypeFromAllShops(new ItemStack(variant), isBuying, player);
                        result.allResults.addAll(variantMatches);
                    }
                    continue;
                }

                String potentialMatName = singleItem.toUpperCase(Locale.ROOT);
                if (potentialMatName.equals("BOOK_AND_QUILL")
                        || potentialMatName.equals("BOOKANDQUILL")) {
                    potentialMatName = "WRITABLE_BOOK";
                } else if (potentialMatName.equals("BOTTLE_O'_ENCHANTING")) {
                    potentialMatName = "EXPERIENCE_BOTTLE";
                }

                Material mat = Material.getMaterial(potentialMatName);

                if (mat != null && mat.isItem()) {
                    result.anyValid = true;

                    List<FoundShopItemModel> foundItems;

                    if (CustomItemMatchers
                            .requiresUnnamedVariantFilter(mat)) {

                        foundItems = FindItemAddOn.getQsApiInstance()
                                .findItemsMatchingFromAllShops(
                                        item -> CustomItemMatchers
                                                .isUnnamedMaterial(
                                                        item,
                                                        mat
                                                ),
                                        isBuying,
                                        player
                                );
                    } else {
                        foundItems = FindItemAddOn.getQsApiInstance()
                                .findItemBasedOnTypeFromAllShops(
                                        new ItemStack(mat),
                                        isBuying,
                                        player
                                );
                    }

                    result.allResults.addAll(foundItems);
                } else {
                    List<FoundShopItemModel> displayNameResults = (List<FoundShopItemModel>) FindItemAddOn.getQsApiInstance()
                            .findItemBasedOnDisplayNameFromAllShops(singleItem, isBuying, player);
                    if (!displayNameResults.isEmpty()) {
                        result.anyValid = true;
                        result.allResults.addAll(displayNameResults);
                    }
                }
            }

            Bukkit.getScheduler().runTask(JavaPlugin.getProvidingPlugin(getClass()), () -> {
                handleShopResults(player, result, isBuying);
            });
        });

        return true;
    }

    private static class ShopSearchResult {
        boolean anyValid = false;
        List<FoundShopItemModel> allResults = new ArrayList<>();
        String originCommand = "wtbmenu";
    }

    private void handleShopResults(Player player, ShopSearchResult result, boolean isBuying) {
        if (!result.anyValid) {
            player.sendMessage(ColorTranslator.translateColorCodes("&cInvalid item, redirecting to menu."));
            player.performCommand("wtbmenu");
        } else {
            if (isBuying) {
                cmdExecutor.openShopMenu(player, result.allResults, false, FindItemAddOn.getConfigProvider().NO_SHOP_FOUND_MSG, result.originCommand, true);
            } else {
                cmdExecutor.openShopMenuDescending(player, result.allResults, false, FindItemAddOn.getConfigProvider().NO_SHOP_FOUND_MSG, "wtsmenu");
            }
        }
    }

    private static Enchantment getEnchantmentByName(String name) {
        String key = name.toLowerCase().replace("_", "").replace(" ", "");
        if (BOOK_NAME_TO_ENCHANTMENT.containsKey(key)) {
            return BOOK_NAME_TO_ENCHANTMENT.get(key);
        }
        for (Enchantment ench : Enchantment.values()) {
            String enchKey = ench.getKey().getKey().toLowerCase().replace("_", "").replace(" ", "");
            if (enchKey.equals(key) || ench.getName().equalsIgnoreCase(name)) {
                return ench;
            }
        }
        return null;
    }

    private static PotionEffectType getPotionEffectByName(String name) {
        String key = name.toUpperCase().replace("_", "").replace(" ", "");
        if (FRIENDLY_POTION_EFFECTS.containsKey(key)) {
            return FRIENDLY_POTION_EFFECTS.get(key);
        }
        for (PotionEffectType effect : PotionEffectType.values()) {
            if (effect == null) continue;
            String effectKey = effect.getName().toUpperCase().replace("_", "").replace(" ", "");
            if (effectKey.equals(key)) {
                return effect;
            }
        }
        return null;
    }
}