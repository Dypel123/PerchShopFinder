package me.perch.shopfinder.handlers.command;

import com.olziedev.playerwarps.api.warp.Warp;
import me.kodysimpson.simpapi.colors.ColorTranslator;
import me.perch.shopfinder.FindItemAddOn;
import me.perch.shopfinder.config.ConfigSetup;
import me.perch.shopfinder.dependencies.EssentialsXPlugin;
import me.perch.shopfinder.dependencies.PlayerWarpsPlugin;
import me.perch.shopfinder.dependencies.WGPlugin;
import me.perch.shopfinder.handlers.gui.PlayerMenuUtility;
import me.perch.shopfinder.handlers.gui.menus.FoundShopsMenu;
import me.perch.shopfinder.models.FoundShopItemModel;
import me.perch.shopfinder.utils.enums.PlayerPermsEnum;
import me.perch.shopfinder.utils.json.HiddenShopStorageUtil;
import me.perch.shopfinder.utils.log.Logger;
import me.perch.shopfinder.utils.warp.EssentialWarpsUtil;
import me.perch.shopfinder.utils.warp.PlayerWarpsUtil;
import me.perch.shopfinder.utils.warp.WGRegionUtils;
import me.perch.shopfinder.utils.warp.WarpUtils;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffectType;
import me.perch.shopfinder.utils.CustomItemMatchers;
import com.ghostchu.quickshop.api.shop.Shop;
import java.util.concurrent.CompletableFuture;

import java.util.*;
import java.util.stream.Collectors;

public class CmdExecutorHandler {

    private static final String THIS_COMMAND_CAN_ONLY_BE_RUN_FROM_IN_GAME = "This command can only be run from in game";

    private static String getPotionEffectSortKey(ItemStack item) {
        if (item == null) return "";
        if (!(item.getItemMeta() instanceof PotionMeta meta)) return "";
        List<String> effects = new ArrayList<>();
        try {
            if (meta.getBasePotionData() != null && meta.getBasePotionData().getType() != null) {
                PotionEffectType effect = meta.getBasePotionData().getType().getEffectType();
                if (effect != null) {
                    effects.add(effect.getName() + ":BASE");
                }
            }
        } catch (Throwable ignored) {}
        if (meta.hasCustomEffects()) {
            for (var custom : meta.getCustomEffects()) {
                effects.add(custom.getType().getName() + ":" + custom.getAmplifier());
            }
        }
        Collections.sort(effects);
        return String.join(";", effects);
    }

    private static void sortShopsByNearbyWarp(List<FoundShopItemModel> shops, Player player) {
        shops.sort((a, b) -> {
            boolean aHasWarp = false;
            boolean bHasWarp = false;
            if (FindItemAddOn.getConfigProvider().NEAREST_WARP_MODE == 2 && PlayerWarpsPlugin.getIsEnabled()) {
                aHasWarp = PlayerWarpsUtil.findNearestWarp(a.getShopLocation(), player, a.getShopOwner()) != null;
                bHasWarp = PlayerWarpsUtil.findNearestWarp(b.getShopLocation(), player, b.getShopOwner()) != null;
            } else if (FindItemAddOn.getConfigProvider().NEAREST_WARP_MODE == 1 && EssentialsXPlugin.isEnabled()) {
                aHasWarp = EssentialWarpsUtil.findNearestWarp(a.getShopLocation()) != null;
                bHasWarp = EssentialWarpsUtil.findNearestWarp(b.getShopLocation()) != null;
            }
            if (aHasWarp == bHasWarp) return 0;
            return aHasWarp ? -1 : 1;
        });
    }

    private static List<FoundShopItemModel> filterRequireNearbyWarp(List<FoundShopItemModel> shops, Player player) {
        if (shops == null || shops.isEmpty()) return shops;

        int mode = FindItemAddOn.getConfigProvider().NEAREST_WARP_MODE;

        boolean canEvaluate =
                (mode == 1 && EssentialsXPlugin.isEnabled()) ||
                        (mode == 2 && PlayerWarpsPlugin.getIsEnabled()) ||
                        (mode == 3 && WGPlugin.isEnabled());

        if (!canEvaluate) return shops;

        List<FoundShopItemModel> out = new ArrayList<>(shops.size());
        for (FoundShopItemModel s : shops) {
            if (s == null) continue;
            if (hasNearbyWarpOrRegion(s, player, mode)) out.add(s);
        }
        return out;
    }

    private static boolean hasNearbyWarpOrRegion(FoundShopItemModel shop, Player player, int mode) {
        Location loc = shop.getShopLocation();
        if (loc == null) return false;

        switch (mode) {
            case 1 -> {
                String w = EssentialWarpsUtil.findNearestWarp(loc);
                return w != null && !StringUtils.isEmpty(w);
            }
            case 2 -> {
                Warp w = PlayerWarpsUtil.findNearestWarp(loc, player, shop.getShopOwner());
                return w != null && !StringUtils.isEmpty(w.getWarpName());
            }
            case 3 -> {
                String r = new WGRegionUtils().findNearestWGRegion(loc);
                return r != null && !StringUtils.isEmpty(r);
            }
            default -> {
                return false;
            }
        }
    }

    public static void sortByDisplayName(List<FoundShopItemModel> list) {
        list.sort(Comparator.comparing(shopItem -> {
            ItemStack item = shopItem.getItemStack();
            if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                return item.getItemMeta().getDisplayName().toLowerCase();
            }
            return "";
        }));
    }

    public void handleShopSearch(String buySellSubCommand, CommandSender commandSender, String itemArg) {
        if (!(commandSender instanceof Player player)) {
            Logger.logInfo(THIS_COMMAND_CAN_ONLY_BE_RUN_FROM_IN_GAME);
            return;
        }
        if (!player.hasPermission(PlayerPermsEnum.FINDITEM_USE.value())) {
            player.sendMessage(ColorTranslator.translateColorCodes(FindItemAddOn.getConfigProvider().PLUGIN_PREFIX + "&cNo permission!"));
            return;
        }

        if (!StringUtils.isEmpty(FindItemAddOn.getConfigProvider().SHOP_SEARCH_LOADING_MSG)) {
            player.sendMessage(ColorTranslator.translateColorCodes(FindItemAddOn.getConfigProvider().PLUGIN_PREFIX + FindItemAddOn.getConfigProvider().SHOP_SEARCH_LOADING_MSG));
        }

        boolean isBuying;
        if (StringUtils.isEmpty(FindItemAddOn.getConfigProvider().FIND_ITEM_TO_BUY_AUTOCOMPLETE) || StringUtils.containsIgnoreCase(FindItemAddOn.getConfigProvider().FIND_ITEM_TO_BUY_AUTOCOMPLETE, " ")) {
            isBuying = buySellSubCommand.equalsIgnoreCase("to_buy");
        } else {
            isBuying = buySellSubCommand.equalsIgnoreCase(FindItemAddOn.getConfigProvider().FIND_ITEM_TO_BUY_AUTOCOMPLETE);
        }

        String originCommand = isBuying ? "wtbmenu" : "wtsmenu";

        if (itemArg.equalsIgnoreCase("*") && !FindItemAddOn.getConfigProvider().FIND_ITEM_CMD_DISABLE_SEARCH_ALL_SHOPS) {
            if (!FindItemAddOn.isQSReremakeInstalled() && FindItemAddOn.getQsApiInstance().isQSShopCacheImplemented()) {
                Logger.logDebugInfo("Should run in async thread...");
                Bukkit.getScheduler().runTaskAsynchronously(FindItemAddOn.getInstance(), () -> {
                    List<FoundShopItemModel> searchResultList = FindItemAddOn.getQsApiInstance().fetchAllItemsFromAllShops(isBuying, player);
                    sortShopsByNearbyWarp(searchResultList, player);
                    if (isBuying) {
                        this.openShopMenu(player, searchResultList, true, FindItemAddOn.getConfigProvider().NO_SHOP_FOUND_MSG, originCommand, true);
                    } else {
                        searchResultList.sort(
                                Comparator.comparing((FoundShopItemModel shop) -> getPotionEffectSortKey(shop.getItemStack()))
                                        .thenComparing(shop -> {
                                            ItemStack item = shop.getItemStack();
                                            return item != null ? item.getType().name() : "";
                                        })
                                        .thenComparingDouble(FoundShopItemModel::getShopPrice)
                        );
                        this.openShopMenuDescending(player, searchResultList, true, FindItemAddOn.getConfigProvider().NO_SHOP_FOUND_MSG, originCommand);
                    }
                });
            } else {
                List<FoundShopItemModel> searchResultList = FindItemAddOn.getQsApiInstance().fetchAllItemsFromAllShops(isBuying, player);
                sortShopsByNearbyWarp(searchResultList, player);
                if (isBuying) {
                    this.openShopMenu(player, searchResultList, false, FindItemAddOn.getConfigProvider().NO_SHOP_FOUND_MSG, originCommand, true);
                } else {
                    searchResultList.sort(
                            Comparator.comparing((FoundShopItemModel shop) -> getPotionEffectSortKey(shop.getItemStack()))
                                    .thenComparing(shop -> {
                                        ItemStack item = shop.getItemStack();
                                        return item != null ? item.getType().name() : "";
                                    })
                                    .thenComparingDouble(FoundShopItemModel::getShopPrice)
                    );
                    this.openShopMenuDescending(player, searchResultList, false, FindItemAddOn.getConfigProvider().NO_SHOP_FOUND_MSG, originCommand);
                }
            }
        } else {
            Material mat = Material.getMaterial(itemArg.toUpperCase());
            if (this.checkMaterialBlacklist(mat)) {
                player.sendMessage(ColorTranslator.translateColorCodes(FindItemAddOn.getConfigProvider().PLUGIN_PREFIX + "&cThis material is not allowed."));
                return;
            }
            if (mat != null && mat.isItem()) {
                Logger.logDebugInfo("Material found: " + mat);
                if (!FindItemAddOn.isQSReremakeInstalled() && FindItemAddOn.getQsApiInstance().isQSShopCacheImplemented()) {
                    Bukkit.getScheduler().runTaskAsynchronously(FindItemAddOn.getInstance(), () -> {
                        List<FoundShopItemModel> searchResultList = FindItemAddOn.getQsApiInstance().findItemBasedOnTypeFromAllShops(new ItemStack(mat), isBuying, player);
                        sortShopsByNearbyWarp(searchResultList, player);
                        if (isBuying) {
                            this.openShopMenu(player, searchResultList, true, FindItemAddOn.getConfigProvider().NO_SHOP_FOUND_MSG, originCommand, true);
                        } else {
                            searchResultList.sort(
                                    Comparator.comparing((FoundShopItemModel shop) -> getPotionEffectSortKey(shop.getItemStack()))
                                            .thenComparing(shop -> {
                                                ItemStack item = shop.getItemStack();
                                                return item != null ? item.getType().name() : "";
                                            })
                                            .thenComparingDouble(FoundShopItemModel::getShopPrice)
                            );
                            this.openShopMenuDescending(player, searchResultList, true, FindItemAddOn.getConfigProvider().NO_SHOP_FOUND_MSG, originCommand);
                        }
                    });
                } else {
                    List<FoundShopItemModel> searchResultList = FindItemAddOn.getQsApiInstance().findItemBasedOnTypeFromAllShops(new ItemStack(mat), isBuying, player);
                    sortShopsByNearbyWarp(searchResultList, player);
                    if (isBuying) {
                        this.openShopMenu(player, searchResultList, false, FindItemAddOn.getConfigProvider().NO_SHOP_FOUND_MSG, originCommand, true);
                    } else {
                        searchResultList.sort(
                                Comparator.comparing((FoundShopItemModel shop) -> getPotionEffectSortKey(shop.getItemStack()))
                                        .thenComparing(shop -> {
                                            ItemStack item = shop.getItemStack();
                                            return item != null ? item.getType().name() : "";
                                        })
                                        .thenComparingDouble(FoundShopItemModel::getShopPrice)
                        );
                        this.openShopMenuDescending(player, searchResultList, false, FindItemAddOn.getConfigProvider().NO_SHOP_FOUND_MSG, originCommand);
                    }
                }
            } else {
                Logger.logDebugInfo("Material not found! Performing query based search..");
                if (!FindItemAddOn.isQSReremakeInstalled() && FindItemAddOn.getQsApiInstance().isQSShopCacheImplemented()) {
                    Bukkit.getScheduler().runTaskAsynchronously(FindItemAddOn.getInstance(), () -> {
                        List<FoundShopItemModel> searchResultList = FindItemAddOn.getQsApiInstance().findItemBasedOnDisplayNameFromAllShops(itemArg, isBuying, player);
                        sortShopsByNearbyWarp(searchResultList, player);
                        if (isBuying) {
                            this.openShopMenu(player, searchResultList, true, FindItemAddOn.getConfigProvider().FIND_ITEM_CMD_INVALID_MATERIAL_MSG, originCommand, true);
                        } else {
                            searchResultList.sort(
                                    Comparator.comparing((FoundShopItemModel shop) -> getPotionEffectSortKey(shop.getItemStack()))
                                            .thenComparing(shop -> {
                                                ItemStack item = shop.getItemStack();
                                                return item != null ? item.getType().name() : "";
                                            })
                                            .thenComparingDouble(FoundShopItemModel::getShopPrice)
                            );
                            this.openShopMenuDescending(player, searchResultList, true, FindItemAddOn.getConfigProvider().FIND_ITEM_CMD_INVALID_MATERIAL_MSG, originCommand);
                        }
                    });
                } else {
                    List<FoundShopItemModel> searchResultList = FindItemAddOn.getQsApiInstance().findItemBasedOnDisplayNameFromAllShops(itemArg, isBuying, player);
                    sortShopsByNearbyWarp(searchResultList, player);
                    if (isBuying) {
                        this.openShopMenu(player, searchResultList, false, FindItemAddOn.getConfigProvider().FIND_ITEM_CMD_INVALID_MATERIAL_MSG, originCommand, true);
                    } else {
                        searchResultList.sort(
                                Comparator.comparing((FoundShopItemModel shop) -> getPotionEffectSortKey(shop.getItemStack()))
                                        .thenComparing(shop -> {
                                            ItemStack item = shop.getItemStack();
                                            return item != null ? item.getType().name() : "";
                                        })
                                        .thenComparingDouble(FoundShopItemModel::getShopPrice)
                        );
                        this.openShopMenuDescending(player, searchResultList, false, FindItemAddOn.getConfigProvider().FIND_ITEM_CMD_INVALID_MATERIAL_MSG, originCommand);
                    }
                }
            }
        }
    }

    public void handleShopSearchForInventory(String buySellSubCommand, Player player) {
        if (!player.hasPermission(PlayerPermsEnum.FINDITEM_USE.value())) {
            player.sendMessage(ColorTranslator.translateColorCodes(
                    FindItemAddOn.getConfigProvider().PLUGIN_PREFIX + "&cNo permission!"));
            return;
        }

        if (!StringUtils.isEmpty(FindItemAddOn.getConfigProvider().SHOP_SEARCH_LOADING_MSG)) {
            player.sendMessage(ColorTranslator.translateColorCodes(
                    FindItemAddOn.getConfigProvider().PLUGIN_PREFIX +
                            FindItemAddOn.getConfigProvider().SHOP_SEARCH_LOADING_MSG));
        }

        boolean isBuying;
        if (StringUtils.isEmpty(FindItemAddOn.getConfigProvider().FIND_ITEM_TO_BUY_AUTOCOMPLETE)
                || StringUtils.containsIgnoreCase(FindItemAddOn.getConfigProvider().FIND_ITEM_TO_BUY_AUTOCOMPLETE, " ")) {
            isBuying = buySellSubCommand.equalsIgnoreCase("to_buy");
        } else {
            isBuying = buySellSubCommand.equalsIgnoreCase(FindItemAddOn.getConfigProvider().FIND_ITEM_TO_BUY_AUTOCOMPLETE);
        }

        String originCommand = isBuying ? "wtbmenu" : "wtsmenu";

        Bukkit.getScheduler().runTaskAsynchronously(FindItemAddOn.getInstance(), () -> {
            Set<Material> uniqueMaterials = new HashSet<>();
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() != Material.AIR) {
                    uniqueMaterials.add(item.getType());
                }
            }

            List<FoundShopItemModel> sellableItems = new ArrayList<>();
            for (Material mat : uniqueMaterials) {
                List<FoundShopItemModel> shops =
                        FindItemAddOn.getQsApiInstance().findItemBasedOnTypeFromAllShops(new ItemStack(mat), isBuying, player);
                if (!shops.isEmpty()) {
                    sellableItems.addAll(shops);
                }
            }

            sortShopsByNearbyWarp(sellableItems, player);
            if (isBuying) {
                openShopMenu(player, sellableItems, false, "&cNo shops found buying any items in your inventory.", originCommand, true);
            } else {
                sellableItems.sort(
                        Comparator.comparing((FoundShopItemModel shop) -> getPotionEffectSortKey(shop.getItemStack()))
                                .thenComparing((FoundShopItemModel shop) -> {
                                    ItemStack item = shop.getItemStack();
                                    return item != null ? item.getType().name() : "";
                                })
                                .thenComparingDouble(FoundShopItemModel::getShopPrice)
                );
                openShopMenuDescending(player, sellableItems, false, "&cNo shops found buying any items in your inventory.", originCommand);
            }
        });
    }

    public void handleShopSearchForEnchantedBook(String buySellSubCommand, CommandSender commandSender, Enchantment enchantment) {
        if (!(commandSender instanceof Player player)) {
            Logger.logInfo(THIS_COMMAND_CAN_ONLY_BE_RUN_FROM_IN_GAME);
            return;
        }
        if (!player.hasPermission(PlayerPermsEnum.FINDITEM_USE.value())) {
            player.sendMessage(ColorTranslator.translateColorCodes(FindItemAddOn.getConfigProvider().PLUGIN_PREFIX + "&cNo permission!"));
            return;
        }

        if (!StringUtils.isEmpty(FindItemAddOn.getConfigProvider().SHOP_SEARCH_LOADING_MSG)) {
            player.sendMessage(ColorTranslator.translateColorCodes(FindItemAddOn.getConfigProvider().PLUGIN_PREFIX + FindItemAddOn.getConfigProvider().SHOP_SEARCH_LOADING_MSG));
        }

        boolean isBuying;
        if (StringUtils.isEmpty(FindItemAddOn.getConfigProvider().FIND_ITEM_TO_BUY_AUTOCOMPLETE) || StringUtils.containsIgnoreCase(FindItemAddOn.getConfigProvider().FIND_ITEM_TO_BUY_AUTOCOMPLETE, " ")) {
            isBuying = buySellSubCommand.equalsIgnoreCase("to_buy");
        } else {
            isBuying = buySellSubCommand.equalsIgnoreCase(FindItemAddOn.getConfigProvider().FIND_ITEM_TO_BUY_AUTOCOMPLETE);
        }

        String originCommand = isBuying ? "wtbmenu" : "wtsmenu";

        Runnable searchTask = () -> {
            List<FoundShopItemModel> allBooks = FindItemAddOn.getQsApiInstance().findItemBasedOnTypeFromAllShops(new ItemStack(Material.ENCHANTED_BOOK), isBuying, player);
            List<FoundShopItemModel> matchingBooks = new ArrayList<>();
            for (FoundShopItemModel shopItem : allBooks) {
                ItemStack item = shopItem.getItemStack();
                if (item != null && item.getType() == Material.ENCHANTED_BOOK && item.hasItemMeta()) {
                    if (item.getItemMeta() instanceof EnchantmentStorageMeta meta) {
                        if (meta.hasStoredEnchant(enchantment)) {
                            matchingBooks.add(shopItem);
                        }
                    }
                }
            }

            if (!matchingBooks.isEmpty()) {
                boolean allSamePrice = matchingBooks.stream()
                        .map(FoundShopItemModel::getShopPrice)
                        .distinct()
                        .count() == 1;
                if (allSamePrice) {
                    java.util.Collections.shuffle(matchingBooks);
                }
            }

            sortShopsByNearbyWarp(matchingBooks, player);
            if (isBuying) {
                openShopMenu(player, matchingBooks, true, "&cNo shops found selling that enchanted book!", originCommand, true);
            } else {
                matchingBooks.sort(
                        Comparator.comparing((FoundShopItemModel shop) -> getPotionEffectSortKey(shop.getItemStack()))
                                .thenComparing(shop -> {
                                    ItemStack item = shop.getItemStack();
                                    return item != null ? item.getType().name() : "";
                                })
                                .thenComparingDouble(FoundShopItemModel::getShopPrice)
                );
                openShopMenuDescending(player, matchingBooks, true, "&cNo shops found selling that enchanted book!", originCommand);
            }
        };

        if (!FindItemAddOn.isQSReremakeInstalled() && FindItemAddOn.getQsApiInstance().isQSShopCacheImplemented()) {
            Bukkit.getScheduler().runTaskAsynchronously(FindItemAddOn.getInstance(), searchTask);
        } else {
            searchTask.run();
        }
    }

    public void handleShopSearchForPotionEffect(String buySellSubCommand, CommandSender commandSender, PotionEffectType effect) {
        if (!(commandSender instanceof Player player)) {
            Logger.logInfo(THIS_COMMAND_CAN_ONLY_BE_RUN_FROM_IN_GAME);
            return;
        }
        if (!player.hasPermission(PlayerPermsEnum.FINDITEM_USE.value())) {
            player.sendMessage(ColorTranslator.translateColorCodes(FindItemAddOn.getConfigProvider().PLUGIN_PREFIX + "&cNo permission!"));
            return;
        }

        if (!StringUtils.isEmpty(FindItemAddOn.getConfigProvider().SHOP_SEARCH_LOADING_MSG)) {
            player.sendMessage(ColorTranslator.translateColorCodes(FindItemAddOn.getConfigProvider().PLUGIN_PREFIX + FindItemAddOn.getConfigProvider().SHOP_SEARCH_LOADING_MSG));
        }

        boolean isBuying;
        if (StringUtils.isEmpty(FindItemAddOn.getConfigProvider().FIND_ITEM_TO_BUY_AUTOCOMPLETE) || StringUtils.containsIgnoreCase(FindItemAddOn.getConfigProvider().FIND_ITEM_TO_BUY_AUTOCOMPLETE, " ")) {
            isBuying = buySellSubCommand.equalsIgnoreCase("to_buy");
        } else {
            isBuying = buySellSubCommand.equalsIgnoreCase(FindItemAddOn.getConfigProvider().FIND_ITEM_TO_BUY_AUTOCOMPLETE);
        }

        String originCommand = isBuying ? "wtbmenu" : "wtsmenu";

        Runnable searchTask = () -> {
            List<Material> potionMaterials = List.of(
                    Material.POTION,
                    Material.SPLASH_POTION,
                    Material.LINGERING_POTION,
                    Material.TIPPED_ARROW
            );
            List<FoundShopItemModel> allPotions = new ArrayList<>();
            for (Material mat : potionMaterials) {
                allPotions.addAll(FindItemAddOn.getQsApiInstance().findItemBasedOnTypeFromAllShops(new ItemStack(mat), isBuying, player));
            }

            List<FoundShopItemModel> matchingPotions = new ArrayList<>();
            for (FoundShopItemModel shopItem : allPotions) {
                ItemStack item = shopItem.getItemStack();
                if (item != null && item.hasItemMeta() && item.getItemMeta() instanceof PotionMeta meta) {
                    try {
                        var data = meta.getBasePotionData();
                        if (data != null && data.getType() != null && data.getType().getEffectType() == effect) {
                            matchingPotions.add(shopItem);
                            continue;
                        }
                    } catch (Throwable ignored) {}
                    if (meta.hasCustomEffects() && meta.getCustomEffects().stream().anyMatch(e -> e.getType().equals(effect))) {
                        matchingPotions.add(shopItem);
                    }
                }
            }

            if (!matchingPotions.isEmpty()) {
                boolean allSamePrice = matchingPotions.stream()
                        .map(FoundShopItemModel::getShopPrice)
                        .distinct()
                        .count() == 1;
                if (allSamePrice) {
                    java.util.Collections.shuffle(matchingPotions);
                }
            }

            sortShopsByNearbyWarp(matchingPotions, player);
            if (isBuying) {
                openShopMenu(player, matchingPotions, true, "&cNo shops found selling potions with that effect.", originCommand, true);
            } else {
                matchingPotions.sort(
                        Comparator.comparing((FoundShopItemModel shop) -> getPotionEffectSortKey(shop.getItemStack()))
                                .thenComparing(shop -> {
                                    ItemStack item = shop.getItemStack();
                                    return item != null ? item.getType().name() : "";
                                })
                                .thenComparingDouble(FoundShopItemModel::getShopPrice)
                );
                openShopMenuDescending(player, matchingPotions, true, "&cNo shops found selling potions with that effect.", originCommand);
            }
        };

        if (!FindItemAddOn.isQSReremakeInstalled() && FindItemAddOn.getQsApiInstance().isQSShopCacheImplemented()) {
            Bukkit.getScheduler().runTaskAsynchronously(FindItemAddOn.getInstance(), searchTask);
        } else {
            searchTask.run();
        }
    }

    public void openShopMenu(Player player, List<FoundShopItemModel> searchResultList, boolean synchronize, String errorMsg, String originCommand, boolean isBuying) {
        searchResultList = filterRequireNearbyWarp(searchResultList, player);

        if (!searchResultList.isEmpty()) {
            List<FoundShopItemModel> finalList;

            if ("wtb_tags".equalsIgnoreCase(originCommand)) {
                sortByDisplayName(searchResultList);
                finalList = new ArrayList<>(searchResultList);
            } else {
                Map<Material, List<FoundShopItemModel>> grouped = new HashMap<>();
                for (FoundShopItemModel shop : searchResultList) {
                    ItemStack item = shop.getItemStack();
                    if (item != null) {
                        grouped.computeIfAbsent(item.getType(), k -> new ArrayList<>()).add(shop);
                    }
                }

                finalList = new ArrayList<>();

                if (isBuying) {
                    List<Material> types = new ArrayList<>(grouped.keySet());
                    Collections.shuffle(types);

                    for (Material type : types) {
                        List<FoundShopItemModel> shops = grouped.get(type);
                        Collections.shuffle(shops);
                        finalList.addAll(shops);
                    }
                } else {
                    List<Material> types = new ArrayList<>(grouped.keySet());
                    types.sort(Comparator.comparing(Enum::name));

                    for (Material type : types) {
                        List<FoundShopItemModel> shops = grouped.get(type);
                        shops.sort(Comparator.comparingDouble(FoundShopItemModel::getShopPrice));
                        finalList.addAll(shops);
                    }
                }
            }

            if (synchronize) {
                Bukkit.getScheduler().runTask(FindItemAddOn.getInstance(), () -> {
                    PlayerMenuUtility util = FindItemAddOn.getPlayerMenuUtility(player);
                    util.setOriginCommand(originCommand);
                    FoundShopsMenu menu = new FoundShopsMenu(util, finalList, isBuying);
                    menu.open(finalList);
                });
            } else {
                PlayerMenuUtility util = FindItemAddOn.getPlayerMenuUtility(player);
                util.setOriginCommand(originCommand);
                FoundShopsMenu menu = new FoundShopsMenu(util, finalList, isBuying);
                menu.open(finalList);
            }
        } else {
            if (!StringUtils.isEmpty(errorMsg)) {
                player.sendMessage(ColorTranslator.translateColorCodes(FindItemAddOn.getConfigProvider().PLUGIN_PREFIX + errorMsg));
            }
        }
    }

    public void openShopMenuDescending(Player player, List<FoundShopItemModel> searchResultList, boolean synchronize, String errorMsg, String originCommand) {
        searchResultList = filterRequireNearbyWarp(searchResultList, player);

        if (!searchResultList.isEmpty()) {
            searchResultList.sort(
                    Comparator.comparing((FoundShopItemModel shop) -> getPotionEffectSortKey(shop.getItemStack()))
                            .thenComparing((FoundShopItemModel shop) -> {
                                ItemStack item = shop.getItemStack();
                                return item != null ? item.getType().name() : "";
                            })
                            .thenComparing(Comparator.comparingDouble(FoundShopItemModel::getShopPrice).reversed())
            );

            Map<Double, List<FoundShopItemModel>> priceGroups = searchResultList.stream()
                    .collect(java.util.stream.Collectors.groupingBy(FoundShopItemModel::getShopPrice));
            List<FoundShopItemModel> sortedAndRandomizedList = new ArrayList<>();
            priceGroups.entrySet().stream()
                    .sorted(Map.Entry.<Double, List<FoundShopItemModel>>comparingByKey().reversed())
                    .forEach(entry -> {
                        List<FoundShopItemModel> group = new ArrayList<>(entry.getValue());
                        java.util.Collections.shuffle(group);
                        sortedAndRandomizedList.addAll(group);
                    });

            if (synchronize) {
                Bukkit.getScheduler().runTask(FindItemAddOn.getInstance(), () -> {
                    PlayerMenuUtility util = FindItemAddOn.getPlayerMenuUtility(player);
                    util.setOriginCommand(originCommand);
                    FoundShopsMenu menu = new FoundShopsMenu(util, sortedAndRandomizedList, false);
                    menu.open(sortedAndRandomizedList);
                });
            } else {
                PlayerMenuUtility util = FindItemAddOn.getPlayerMenuUtility(player);
                util.setOriginCommand(originCommand);
                FoundShopsMenu menu = new FoundShopsMenu(util, sortedAndRandomizedList, false);
                menu.open(sortedAndRandomizedList);
            }
        } else {
            if (!StringUtils.isEmpty(errorMsg)) {
                player.sendMessage(ColorTranslator.translateColorCodes(FindItemAddOn.getConfigProvider().PLUGIN_PREFIX + errorMsg));
            }
        }
    }

    private boolean checkMaterialBlacklist(Material mat) {
        return FindItemAddOn.getConfigProvider().getBlacklistedMaterials().contains(mat);
    }

    public void handleHideShop(CommandSender commandSender) {
        if (commandSender instanceof Player player) {
            if (player.hasPermission(PlayerPermsEnum.FINDITEM_HIDESHOP.value())) {
                Block playerLookAtBlock = player.getTargetBlock(null, 3);
                Logger.logDebugInfo("TargetBlock found: " + playerLookAtBlock.getType());
                hideHikariShop(
                        (com.ghostchu.quickshop.api.shop.Shop) FindItemAddOn.getQsApiInstance().findShopAtLocation(playerLookAtBlock),
                        player
                );
            } else {
                player.sendMessage(ColorTranslator.translateColorCodes(
                        FindItemAddOn.getConfigProvider().PLUGIN_PREFIX + "&cNo permission!"
                ));
            }
        } else {
            Logger.logInfo(THIS_COMMAND_CAN_ONLY_BE_RUN_FROM_IN_GAME);
        }
    }

    public void handleRevealShop(CommandSender commandSender) {
        if (!(commandSender instanceof Player)) {
            Logger.logInfo(THIS_COMMAND_CAN_ONLY_BE_RUN_FROM_IN_GAME);
            return;
        }

        Player player = (Player) commandSender;
        if (player.hasPermission(PlayerPermsEnum.FINDITEM_HIDESHOP.value())) {
            Block playerLookAtBlock = player.getTargetBlock(null, 5);
            if (playerLookAtBlock != null) {
                Logger.logDebugInfo("TargetBlock found: " + playerLookAtBlock.getType());
                revealShop(
                        (com.ghostchu.quickshop.api.shop.Shop) FindItemAddOn.getQsApiInstance().findShopAtLocation(playerLookAtBlock),
                        player
                );
            } else {
                Logger.logDebugInfo("TargetBlock is null!");
            }
        } else {
            player.sendMessage(ColorTranslator.translateColorCodes(
                    FindItemAddOn.getConfigProvider().PLUGIN_PREFIX + "&cNo permission!"
            ));
        }
    }

    public void handlePluginReload(CommandSender commandSender) {
        if (!(commandSender instanceof Player player)) {
            ConfigSetup.reloadConfig();
            ConfigSetup.checkForMissingProperties();
            ConfigSetup.saveConfig();
            FindItemAddOn.initConfigProvider();

            List<com.ghostchu.quickshop.api.shop.Shop> allServerShops = FindItemAddOn.getQsApiInstance().getAllShops();
            if (allServerShops.isEmpty()) {
                Logger.logWarning("&6Found &e0 &6shops on the server. If you ran &e/qs reload &6recently, please restart your server!");
            } else {
                Logger.logInfo("&aFound &e" + allServerShops.size() + " &ashops on the server.");
            }
            WarpUtils.updateWarps();
        } else {
            if (player.hasPermission(PlayerPermsEnum.FINDITEM_RELOAD.value()) || player.hasPermission(PlayerPermsEnum.FINDITEM_ADMIN.value())) {
                ConfigSetup.reloadConfig();
                ConfigSetup.checkForMissingProperties();
                ConfigSetup.saveConfig();
                FindItemAddOn.initConfigProvider();

                player.sendMessage(ColorTranslator.translateColorCodes(
                        FindItemAddOn.getConfigProvider().PLUGIN_PREFIX + "&aConfig reloaded!"
                ));

                List<com.ghostchu.quickshop.api.shop.Shop> allServerShops = FindItemAddOn.getQsApiInstance().getAllShops();
                if (allServerShops.isEmpty()) {
                    player.sendMessage(ColorTranslator.translateColorCodes(
                            FindItemAddOn.getConfigProvider().PLUGIN_PREFIX +
                                    "&6Found &e0 &6shops on the server. If you ran &e/qs reload &6recently, please restart your server!"
                    ));
                } else {
                    player.sendMessage(ColorTranslator.translateColorCodes(
                            FindItemAddOn.getConfigProvider().PLUGIN_PREFIX +
                                    "&aFound &e" + allServerShops.size() + " &ashops on the server."
                    ));
                }
                WarpUtils.updateWarps();
            } else {
                player.sendMessage(ColorTranslator.translateColorCodes(
                        FindItemAddOn.getConfigProvider().PLUGIN_PREFIX + "&cNo permission!"
                ));
            }
        }
    }

    @Deprecated(forRemoval = true)
    public void handlePluginRestart(CommandSender commandSender) {
        if (!(commandSender instanceof Player)) {
            Bukkit.getPluginManager().disablePlugin(FindItemAddOn.getInstance());
            Bukkit.getPluginManager().enablePlugin(FindItemAddOn.getPlugin(FindItemAddOn.class));
            Logger.logInfo("&aPlugin restarted!");
            List allServerShops = FindItemAddOn.getQsApiInstance().getAllShops();
            if (allServerShops.size() == 0) {
                Logger.logWarning("&6Found &e0 &6shops on the server. If you ran &e/qs reload &6recently, please restart your server!");
            } else {
                Logger.logInfo("&aFound &e" + allServerShops.size() + " &ashops on the server.");
            }
        } else {
            Player player = (Player) commandSender;
            if (player.hasPermission(PlayerPermsEnum.FINDITEM_RESTART.value()) || player.hasPermission(PlayerPermsEnum.FINDITEM_ADMIN.value())) {
                Bukkit.getPluginManager().disablePlugin(FindItemAddOn.getInstance());
                Bukkit.getPluginManager().enablePlugin(FindItemAddOn.getPlugin(FindItemAddOn.class));
                player.sendMessage(ColorTranslator.translateColorCodes(FindItemAddOn.getConfigProvider().PLUGIN_PREFIX + "&aPlugin restarted!"));
                List allServerShops = FindItemAddOn.getQsApiInstance().getAllShops();
                if (allServerShops.size() == 0) {
                    player.sendMessage(ColorTranslator.translateColorCodes(
                            FindItemAddOn.getConfigProvider().PLUGIN_PREFIX
                                    + "&6Found &e0 &6shops on the server. If you ran &e/qs reload &6recently, please restart your server!"));
                } else {
                    player.sendMessage(ColorTranslator.translateColorCodes(
                            FindItemAddOn.getConfigProvider().PLUGIN_PREFIX + "&aFound &e" + allServerShops.size() + " &ashops on the server."));
                }
            } else {
                player.sendMessage(ColorTranslator.translateColorCodes(FindItemAddOn.getConfigProvider().PLUGIN_PREFIX + "&cNo permission!"));
            }
        }
    }

    private void hideHikariShop(com.ghostchu.quickshop.api.shop.Shop shop, Player player) {
        if (shop != null) {
            if (FindItemAddOn.getQsApiInstance().isShopOwnerCommandRunner(player, shop)) {
                if (!HiddenShopStorageUtil.isShopHidden(shop)) {
                    HiddenShopStorageUtil.handleShopSearchVisibilityAsync(shop, true);
                    player.sendMessage(ColorTranslator.translateColorCodes(
                            FindItemAddOn.getConfigProvider().PLUGIN_PREFIX
                                    + FindItemAddOn.getConfigProvider().FIND_ITEM_CMD_SHOP_HIDE_SUCCESS_MSG));
                } else {
                    player.sendMessage(ColorTranslator.translateColorCodes(
                            FindItemAddOn.getConfigProvider().PLUGIN_PREFIX
                                    + FindItemAddOn.getConfigProvider().FIND_ITEM_CMD_SHOP_ALREADY_HIDDEN_MSG));
                }
            } else {
                player.sendMessage(ColorTranslator.translateColorCodes(
                        FindItemAddOn.getConfigProvider().PLUGIN_PREFIX
                                + FindItemAddOn.getConfigProvider().FIND_ITEM_CMD_HIDING_SHOP_OWNER_INVALID_MSG));
            }
        } else {
            player.sendMessage(ColorTranslator.translateColorCodes(
                    FindItemAddOn.getConfigProvider().PLUGIN_PREFIX
                            + FindItemAddOn.getConfigProvider().FIND_ITEM_CMD_INVALID_SHOP_BLOCK_MSG));
        }
    }

    private void revealShop(com.ghostchu.quickshop.api.shop.Shop shop, Player player) {
        if (shop != null) {
            if (FindItemAddOn.getQsApiInstance().isShopOwnerCommandRunner(player, shop)) {
                if (HiddenShopStorageUtil.isShopHidden(shop)) {
                    HiddenShopStorageUtil.handleShopSearchVisibilityAsync(shop, false);
                    player.sendMessage(ColorTranslator.translateColorCodes(
                            FindItemAddOn.getConfigProvider().PLUGIN_PREFIX
                                    + FindItemAddOn.getConfigProvider().FIND_ITEM_CMD_SHOP_REVEAL_SUCCESS_MSG));
                } else {
                    player.sendMessage(ColorTranslator.translateColorCodes(
                            FindItemAddOn.getConfigProvider().PLUGIN_PREFIX
                                    + FindItemAddOn.getConfigProvider().FIND_ITEM_CMD_SHOP_ALREADY_PUBLIC_MSG));
                }
            } else {
                player.sendMessage(ColorTranslator.translateColorCodes(
                        FindItemAddOn.getConfigProvider().PLUGIN_PREFIX
                                + FindItemAddOn.getConfigProvider().FIND_ITEM_CMD_HIDING_SHOP_OWNER_INVALID_MSG));
            }
        } else {
            player.sendMessage(ColorTranslator.translateColorCodes(
                    FindItemAddOn.getConfigProvider().PLUGIN_PREFIX
                            + FindItemAddOn.getConfigProvider().FIND_ITEM_CMD_INVALID_SHOP_BLOCK_MSG));
        }
    }

    public void handleOutOfStockMenu(Player player) {
        if (!player.hasPermission(PlayerPermsEnum.FINDITEM_USE.value())) {
            player.sendMessage(ColorTranslator.translateColorCodes(
                    FindItemAddOn.getConfigProvider().PLUGIN_PREFIX
                            + "&cNo permission!"
            ));
            return;
        }

        if (!StringUtils.isEmpty(
                FindItemAddOn.getConfigProvider()
                        .SHOP_SEARCH_LOADING_MSG
        )) {
            player.sendMessage(ColorTranslator.translateColorCodes(
                    FindItemAddOn.getConfigProvider().PLUGIN_PREFIX
                            + FindItemAddOn.getConfigProvider()
                            .SHOP_SEARCH_LOADING_MSG
            ));
        }

        Bukkit.getScheduler().runTaskAsynchronously(
                FindItemAddOn.getInstance(),
                () -> {
                    var qsApi = FindItemAddOn.getQsApiInstance();

                    List<Shop> ownedShops =
                            qsApi.getAllShops(player.getUniqueId());

                    List<FoundShopItemModel> outOfStockModels =
                            new ArrayList<>();

                    final int batchSize = 16;

                    for (int batchStart = 0;
                         batchStart < ownedShops.size();
                         batchStart += batchSize) {

                        int batchEnd = Math.min(
                                batchStart + batchSize,
                                ownedShops.size()
                        );

                        List<Shop> shopsInBatch =
                                ownedShops.subList(
                                        batchStart,
                                        batchEnd
                                );

                        List<CompletableFuture<Integer>> stockQueries =
                                new ArrayList<>(shopsInBatch.size());

                        for (Shop shop : shopsInBatch) {
                            stockQueries.add(
                                    qsApi.getRemainingStockOrSpaceFuture(
                                            shop,
                                            true
                                    )
                            );
                        }

                        // wait for the entire batch
                        CompletableFuture<?>[] pendingQueries =
                                stockQueries.toArray(
                                        CompletableFuture<?>[]::new
                                );

                        CompletableFuture.allOf(
                                pendingQueries
                        ).join();

                        for (int index = 0;
                             index < shopsInBatch.size();
                             index++) {

                            int remainingStock =
                                    stockQueries.get(index).join();

                            // -1 = unlimited shop
                            // -2 = unknown cached stock
                            if (remainingStock != 0) {
                                continue;
                            }

                            Shop shop = shopsInBatch.get(index);
                            Location location =
                                    shop.bukkitLocation();

                            if (location == null
                                    || location.getWorld() == null) {
                                continue;
                            }

                            outOfStockModels.add(
                                    new FoundShopItemModel(
                                            shop.getPrice(),
                                            remainingStock,
                                            shop.getOwner()
                                                    .getUniqueIdOptional()
                                                    .orElse(
                                                            new UUID(0, 0)
                                                    ),
                                            location,
                                            shop.getItem(),
                                            shop.isBuying()
                                    )
                            );
                        }
                    }

                    outOfStockModels.sort(
                            Comparator.comparing(
                                            (FoundShopItemModel shop) ->
                                                    getPotionEffectSortKey(
                                                            shop.getItemStack()
                                                    )
                                    )
                                    .thenComparing(shop -> {
                                        ItemStack item =
                                                shop.getItemStack();

                                        return item != null
                                                ? item.getType().name()
                                                : "";
                                    })
                                    .thenComparingDouble(
                                            FoundShopItemModel::getShopPrice
                                    )
                    );

                    Bukkit.getScheduler().runTask(
                            FindItemAddOn.getInstance(),
                            () -> openShopMenu(
                                    player,
                                    outOfStockModels,
                                    false,
                                    "&cYou have no out-of-stock shops.",
                                    "wtsmenu",
                                    false
                            )
                    );
                }
        );
    }

    public void handleShopSearchForUnbreakable(String buySellSubCommand, CommandSender commandSender) {
        if (!(commandSender instanceof Player player)) {
            Logger.logInfo(THIS_COMMAND_CAN_ONLY_BE_RUN_FROM_IN_GAME);
            return;
        }
        if (!player.hasPermission(PlayerPermsEnum.FINDITEM_USE.value())) {
            player.sendMessage(ColorTranslator.translateColorCodes(
                    FindItemAddOn.getConfigProvider().PLUGIN_PREFIX + "&cNo permission!"));
            return;
        }

        if (!StringUtils.isEmpty(FindItemAddOn.getConfigProvider().SHOP_SEARCH_LOADING_MSG)) {
            player.sendMessage(ColorTranslator.translateColorCodes(
                    FindItemAddOn.getConfigProvider().PLUGIN_PREFIX +
                            FindItemAddOn.getConfigProvider().SHOP_SEARCH_LOADING_MSG));
        }

        boolean isBuying;
        if (StringUtils.isEmpty(FindItemAddOn.getConfigProvider().FIND_ITEM_TO_BUY_AUTOCOMPLETE)
                || StringUtils.containsIgnoreCase(FindItemAddOn.getConfigProvider().FIND_ITEM_TO_BUY_AUTOCOMPLETE, " ")) {
            isBuying = buySellSubCommand.equalsIgnoreCase("to_buy");
        } else {
            isBuying = buySellSubCommand.equalsIgnoreCase(FindItemAddOn.getConfigProvider().FIND_ITEM_TO_BUY_AUTOCOMPLETE);
        }

        String originCommand = isBuying ? "wtbmenu" : "wtsmenu";

        Runnable searchTask = () -> {
            List<FoundShopItemModel> unbreakableItems =
                    FindItemAddOn.getQsApiInstance()
                            .findItemsMatchingFromAllShops(
                                    CustomItemMatchers::isUnbreakable,
                                    isBuying,
                                    player
                            );

            sortShopsByNearbyWarp(unbreakableItems, player);
            if (isBuying) {
                openShopMenu(player, unbreakableItems, true, "&cNo shops found selling unbreakable items!", originCommand, true);
            } else {
                unbreakableItems.sort(
                        Comparator.comparing((FoundShopItemModel shop) -> getPotionEffectSortKey(shop.getItemStack()))
                                .thenComparing(shop -> {
                                    ItemStack item = shop.getItemStack();
                                    return item != null ? item.getType().name() : "";
                                })
                                .thenComparingDouble(FoundShopItemModel::getShopPrice)
                );
                openShopMenuDescending(player, unbreakableItems, true, "&cNo shops found selling unbreakable items!", originCommand);
            }
        };

        if (!FindItemAddOn.isQSReremakeInstalled() && FindItemAddOn.getQsApiInstance().isQSShopCacheImplemented()) {
            Bukkit.getScheduler().runTaskAsynchronously(FindItemAddOn.getInstance(), searchTask);
        } else {
            searchTask.run();
        }
    }
}
