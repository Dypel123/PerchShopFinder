package me.perch.shopfinder.commands;

import me.perch.shopfinder.FindItemAddOn;
import me.perch.shopfinder.handlers.command.CmdExecutorHandler;
import me.perch.shopfinder.models.FoundShopItemModel;
import me.kodysimpson.simpapi.colors.ColorTranslator;
import net.milkbowl.vault.economy.Economy;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import me.perch.shopfinder.utils.CustomItemMatchers;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class WhereToSellCommand implements CommandExecutor {

    private final CmdExecutorHandler cmdExecutor;
    private final String sellCommand;

    private static Economy ECO;

    private static final Map<String, PotionEffectType> FRIENDLY_POTION_EFFECTS = buildFriendlyPotionEffectMap();
    private static final Map<String, Enchantment> BOOK_NAME_TO_ENCHANTMENT = buildBookNameToEnchantmentMap();

    private static Map<String, PotionEffectType> buildFriendlyPotionEffectMap() {
        Map<String, PotionEffectType> map = new HashMap<>();
        map.put("HASTE", PotionEffectType.getByName("FAST_DIGGING"));
        map.put("MININGFATIGUE", PotionEffectType.getByName("SLOW_DIGGING"));
        map.put("STRENGTH", PotionEffectType.getByName("INCREASE_DAMAGE"));
        map.put("INSTANTHEALTH", PotionEffectType.getByName("HEAL"));
        map.put("INSTANTHARM", PotionEffectType.getByName("HARM"));
        map.put("HARM", PotionEffectType.getByName("HARM"));
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
        map.put("SPEED", PotionEffectType.getByName("SPEED"));
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

        // Missing Aliases Added
        map.put("HARMING", PotionEffectType.getByName("HARM"));
        map.put("LEAPING", PotionEffectType.getByName("JUMP"));
        map.put("REGEN", PotionEffectType.getByName("REGENERATION"));
        map.put("HEALING", PotionEffectType.getByName("HEAL"));
        map.put("DECAY", PotionEffectType.getByName("WITHER"));
        map.put("DULLNESS", PotionEffectType.getByName("SLOW_DIGGING"));

        // TURTLEMASTER removed for explicit handling below
        return map;
    }

    private static Map<String, Enchantment> buildBookNameToEnchantmentMap() {
        Map<String, String> special = new HashMap<>();
        special.put("BINDING_CURSE", "CURSEOFBINDING");
        special.put("VANISHING_CURSE", "CURSEOFVANISHING");

        Map<String, Enchantment> map = new HashMap<>();
        for (Enchantment ench : Enchantment.values()) {
            String key = ench.getKey().getKey().toUpperCase(Locale.ROOT);
            String bookName = special.getOrDefault(key, key.replace("_", ""));
            map.put(bookName.toLowerCase(Locale.ROOT), ench);
            if (bookName.equals("CURSEOFBINDING")) map.put("bindingcurse", ench);
            if (bookName.equals("CURSEOFVANISHING")) map.put("vanishingcurse", ench);
        }
        return map;
    }

    private static String[] remapArtAliases(String[] args) {
        if (args == null || args.length == 0) return args;
        String a0 = args[0].toLowerCase(Locale.ROOT);
        switch (a0) {
            case "art":
                return new String[] { "lore:copyright", "lore:artwork" };
            case "artmap":
                return new String[] { "lore:artwork" };
            case "playtime":
                return new String[] { "lore:playtime" };
            case "mapart":
                return new String[] { "lore:copyright" };
            case "claimblocks":
                return new String[] { "lore:claims" };
            default:
                return args;
        }
    }

    public WhereToSellCommand() {
        this.cmdExecutor = new CmdExecutorHandler();
        if (StringUtils.isEmpty(FindItemAddOn.getConfigProvider().FIND_ITEM_TO_SELL_AUTOCOMPLETE)
                || StringUtils.containsIgnoreCase(FindItemAddOn.getConfigProvider().FIND_ITEM_TO_SELL_AUTOCOMPLETE, " ")) {
            this.sellCommand = "TO_SELL";
        } else {
            this.sellCommand = FindItemAddOn.getConfigProvider().FIND_ITEM_TO_SELL_AUTOCOMPLETE;
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
                player.performCommand("wtsmenu");
            });
            return true;
        }

        String firstArg = args[0];
        if (firstArg.equalsIgnoreCase("inv")) {
            cmdExecutor.handleShopSearchForInventory(sellCommand, player);
            return true;
        }

        args = remapArtAliases(args);
        String[] searchArgs = args.clone();

        if (firstArg.equalsIgnoreCase("hand")) {
            Material handMat = player.getInventory().getItemInMainHand().getType();
            if (handMat == Material.AIR) {
                sender.sendMessage(ColorTranslator.translateColorCodes(
                        FindItemAddOn.getConfigProvider().PLUGIN_PREFIX + "&cYou are not holding any item!"));
                return true;
            }
            searchArgs[0] = handMat.name();
        }

        if (firstArg.equalsIgnoreCase("unbreakable")) {
            cmdExecutor.handleShopSearchForUnbreakable(sellCommand, sender);
            return true;
        }

        boolean isSelling = sellCommand.equalsIgnoreCase("TO_SELL") ||
                sellCommand.equalsIgnoreCase(FindItemAddOn.getConfigProvider().FIND_ITEM_TO_SELL_AUTOCOMPLETE);

        Bukkit.getScheduler().runTaskAsynchronously(JavaPlugin.getProvidingPlugin(getClass()), () -> {
            ShopSearchResult result = new ShopSearchResult();

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
                                            !isSelling,
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
                                            !isSelling,
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
                                .findItemBasedOnTypeFromAllShops(new ItemStack(brownEgg), !isSelling, player);

                        List<FoundShopItemModel> caughtMobs = eggItems.stream()
                                .filter(shopItem -> {
                                    ItemStack item = shopItem.getItemStack();
                                    if (item == null || !item.hasItemMeta()) return false;

                                    if (item.getItemMeta().hasLore()) {
                                        List<String> lore = item.getItemMeta().getLore();
                                        if (lore != null) {
                                            for (String line : lore) {
                                                String lowerLine = line.toLowerCase(Locale.ROOT);
                                                // Check for 'contains' OR 'catch'
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
                                            !isSelling,
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
                                            !isSelling,
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
                                            !isSelling,
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
                                            !isSelling,
                                            player
                                    )
                    );

                    continue;
                }

                if (singleItem.equalsIgnoreCase("shulker_box")) {
                    result.anyValid = true;

                    result.allResults.addAll(
                            FindItemAddOn.getQsApiInstance()
                                    .findItemsMatchingFromAllShops(
                                            CustomItemMatchers::isShulkerBox,
                                            !isSelling,
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
                                        .findItemBasedOnTypeFromAllShops(new ItemStack(variant), !isSelling, player);
                        result.allResults.addAll(variantMatches);
                    }
                    continue;
                }

                // Check for aliases (BOOK_AND_QUILL -> WRITABLE_BOOK)
                String potentialMatName = singleItem.toUpperCase(Locale.ROOT);
                if (potentialMatName.equals("BOOK_AND_QUILL") || potentialMatName.equals("BOOKANDQUILL")) {
                    potentialMatName = "WRITABLE_BOOK";
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
                                        !isSelling,
                                        player
                                );
                    } else {
                        foundItems = FindItemAddOn.getQsApiInstance()
                                .findItemBasedOnTypeFromAllShops(
                                        new ItemStack(mat),
                                        !isSelling,
                                        player
                                );
                    }

                    result.allResults.addAll(foundItems);
                } else {
                    List<FoundShopItemModel> displayNameResults = (List<FoundShopItemModel>) FindItemAddOn.getQsApiInstance()
                            .findItemBasedOnDisplayNameFromAllShops(singleItem, !isSelling, player);
                    if (!displayNameResults.isEmpty()) {
                        result.anyValid = true;
                        result.allResults.addAll(displayNameResults);
                    }
                }
            }

            Bukkit.getScheduler().runTask(JavaPlugin.getProvidingPlugin(getClass()), () -> {
                handleShopResults(player, result);
            });
        });

        return true;
    }

    private static class ShopSearchResult {
        boolean anyValid = false;
        List<FoundShopItemModel> allResults = new ArrayList<>();
    }

    private void handleShopResults(Player player, ShopSearchResult result) {
        boolean isSelling = sellCommand.equalsIgnoreCase("TO_SELL") ||
                sellCommand.equalsIgnoreCase(FindItemAddOn.getConfigProvider().FIND_ITEM_TO_SELL_AUTOCOMPLETE);

        if (!result.anyValid) {
            player.sendMessage(ColorTranslator.translateColorCodes("&cInvalid item, redirecting to menu."));
            player.performCommand("wtsmenu");
            return;
        }

        if (!isSelling) {
            cmdExecutor.openShopMenuDescending(player, result.allResults, false, FindItemAddOn.getConfigProvider().NO_SHOP_FOUND_MSG, "wtsmenu");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(JavaPlugin.getProvidingPlugin(getClass()), () -> {
            Set<UUID> owners = new HashSet<>();
            for (FoundShopItemModel m : result.allResults) {
                OfflinePlayer op = resolveOwner(m);
                if (op != null) owners.add(op.getUniqueId());
            }

            CompletableFuture<Map<UUID, Double>> balFuture = new CompletableFuture<>();
            Bukkit.getScheduler().runTask(JavaPlugin.getProvidingPlugin(getClass()), () -> {
                ensureEconomy();
                Map<UUID, Double> map = new HashMap<>();
                if (ECO != null) {
                    for (UUID id : owners) {
                        try {
                            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
                            if (op == null) continue;
                            if (!op.hasPlayedBefore() && !op.isOnline()) continue;
                            double bal;
                            try {
                                bal = ECO.getBalance(op);
                            } catch (Throwable t) {
                                continue;
                            }
                            if (bal < 0D) bal = 0D;
                            map.put(id, bal);
                        } catch (Throwable ignored) {
                        }
                    }
                }
                balFuture.complete(map);
            });

            Map<UUID, Double> balances = balFuture.join();
            List<FoundShopItemModel> filtered = filterByBalances(result.allResults, balances);

            Bukkit.getScheduler().runTask(JavaPlugin.getProvidingPlugin(getClass()), () -> {
                cmdExecutor.openShopMenuDescending(player, filtered, false, FindItemAddOn.getConfigProvider().NO_SHOP_FOUND_MSG, "wtsmenu");
            });
        });
    }

    private static Enchantment getEnchantmentByName(String name) {
        String key = name.toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "");
        if (BOOK_NAME_TO_ENCHANTMENT.containsKey(key)) {
            return BOOK_NAME_TO_ENCHANTMENT.get(key);
        }
        for (Enchantment ench : Enchantment.values()) {
            String enchKey = ench.getKey().getKey().toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "");
            if (enchKey.equals(key) || ench.getName().equalsIgnoreCase(name)) {
                return ench;
            }
        }
        return null;
    }

    private static PotionEffectType getPotionEffectByName(String name) {
        String key = name.toUpperCase(Locale.ROOT).replace("_", "").replace(" ", "");
        if (FRIENDLY_POTION_EFFECTS.containsKey(key)) {
            return FRIENDLY_POTION_EFFECTS.get(key);
        }
        for (PotionEffectType effect : PotionEffectType.values()) {
            if (effect == null) continue;
            String effectKey = effect.getName().toUpperCase(Locale.ROOT).replace("_", "").replace(" ", "");
            if (effectKey.equals(key)) {
                return effect;
            }
        }
        return null;
    }

    private static void ensureEconomy() {
        if (ECO != null) return;
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp != null) ECO = rsp.getProvider();
    }

    private static List<FoundShopItemModel> filterByBalances(List<FoundShopItemModel> items, Map<UUID, Double> balances) {
        List<FoundShopItemModel> out = new ArrayList<>(items.size());
        for (FoundShopItemModel m : items) {
            OfflinePlayer owner = resolveOwner(m);
            if (owner == null) {
                out.add(m);
                continue;
            }
            UUID id = owner.getUniqueId();
            Double bal = balances.get(id);
            if (bal == null) {
                out.add(m);
                continue;
            }
            if (bal >= safePrice(m)) out.add(m);
        }
        return out;
    }

    private static double safePrice(FoundShopItemModel m) {
        try {
            return Math.max(0D, m.getShopPrice());
        } catch (Throwable t) {
            return 0D;
        }
    }

    private static OfflinePlayer resolveOwner(FoundShopItemModel m) {
        try {
            Method uuidM = tryMethod(m.getClass(), "getShopOwnerUuid", "getOwnerUuid", "getOwnerUUID", "getOwnerId", "getShopOwnerId");
            if (uuidM != null) {
                Object o = uuidM.invoke(m);
                if (o instanceof UUID u) return Bukkit.getOfflinePlayer(u);
                if (o instanceof String s) return Bukkit.getOfflinePlayer(UUID.fromString(s));
            }
        } catch (Throwable ignored) {
        }
        try {
            Method playerM = tryMethod(m.getClass(), "getShopOwner", "getOwner", "getPlayer", "getShopPlayer");
            if (playerM != null) {
                Object o = playerM.invoke(m);
                if (o instanceof OfflinePlayer op) return op;
                if (o instanceof UUID u) return Bukkit.getOfflinePlayer(u);
                if (o instanceof String s) {
                    try {
                        return Bukkit.getOfflinePlayer(UUID.fromString(s));
                    } catch (Throwable e) {
                        return Bukkit.getOfflinePlayer(s);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            Method nameM = tryMethod(m.getClass(), "getShopOwnerName", "getOwnerName", "getSellerName");
            if (nameM != null) {
                Object o = nameM.invoke(m);
                if (o instanceof String s) return Bukkit.getOfflinePlayer(s);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Method tryMethod(Class<?> c, String... names) {
        for (String n : names) {
            try {
                Method m = c.getMethod(n);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }
}