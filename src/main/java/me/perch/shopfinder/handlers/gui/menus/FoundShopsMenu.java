package me.perch.shopfinder.handlers.gui.menus;

import me.perch.shopfinder.config.ConfigProvider;
import me.perch.shopfinder.dependencies.EssentialsXPlugin;
import me.perch.shopfinder.dependencies.PlayerWarpsPlugin;
import me.perch.shopfinder.dependencies.WGPlugin;
import me.perch.shopfinder.FindItemAddOn;
import me.perch.shopfinder.handlers.gui.PaginatedMenu;
import me.perch.shopfinder.handlers.gui.PlayerMenuUtility;
import me.perch.shopfinder.models.FoundShopItemModel;
import me.perch.shopfinder.utils.enums.CustomCmdPlaceholdersEnum;
import me.perch.shopfinder.utils.enums.PlayerPermsEnum;
import me.perch.shopfinder.utils.enums.ShopLorePlaceholdersEnum;
import me.perch.shopfinder.utils.json.ShopSearchActivityStorageUtil;
import me.perch.shopfinder.utils.LocationUtils;
import me.perch.shopfinder.utils.log.Logger;
import me.perch.shopfinder.utils.warp.EssentialWarpsUtil;
import me.perch.shopfinder.utils.warp.PlayerWarpsUtil;
import me.perch.shopfinder.utils.warp.WGRegionUtils;
import com.olziedev.playerwarps.api.warp.Warp;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import io.papermc.lib.PaperLib;
import me.kodysimpson.simpapi.colors.ColorTranslator;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import me.perch.shopfinder.utils.ShopHighlighter;
import me.perch.shopfinder.utils.HighlightColourManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class FoundShopsMenu extends PaginatedMenu {

    public static final String SHOP_STOCK_UNLIMITED = "Unlimited";
    public static final String SHOP_STOCK_UNKNOWN = "Unknown";
    private static final String NAMEDSPACE_KEY_LOCATION_DATA = "locationData";
    private final ConfigProvider configProvider;
    private final List<FoundShopItemModel> currentPageShops = new ArrayList<>();
    private final boolean isBuying;

    // -- Optimization: Caches --
    private static final Map<UUID, String> ownerNameCache = new ConcurrentHashMap<>();

    // Instance-based cache for per-player favorites.
    // Stores: <ShopItem, Data>
    private final Map<FoundShopItemModel, CachedShopData> menuDataCache = new ConcurrentHashMap<>();

    private boolean hasBeenSorted = false;
    private BukkitTask loadingTask;
    private int currentRenderId = 0;

    public FoundShopsMenu(PlayerMenuUtility playerMenuUtility, List<FoundShopItemModel> searchResult, boolean isBuying) {
        super(playerMenuUtility, searchResult);
        configProvider = FindItemAddOn.getConfigProvider();
        this.isBuying = isBuying;

        List<FoundShopItemModel> list =
                super.playerMenuUtility.getPlayerShopSearchResult();

        if (list != null && !list.isEmpty() && !isBuying) {
            // Java's stable sort preserves the randomized order
            // between shops having the same price.
            list.sort(
                    (first, second) -> Double.compare(
                            second.getShopPrice(),
                            first.getShopPrice()
                    )
            );
        }
    }

    public FoundShopsMenu(PlayerMenuUtility playerMenuUtility, List<FoundShopItemModel> searchResult) {
        this(playerMenuUtility, searchResult, false);
    }

    @Override
    public String getMenuName() {
        if (!StringUtils.isEmpty(configProvider.SHOP_SEARCH_GUI_TITLE)) {
            return ColorTranslator.translateColorCodes(configProvider.SHOP_SEARCH_GUI_TITLE);
        } else {
            return ColorTranslator.translateColorCodes("&l» &rShops");
        }
    }

    @Override
    public int getSlots() {
        return 54;
    }

    @Override
    public void handleMenu(@NotNull InventoryClickEvent event) {
        int slot = event.getSlot();
        Player player = (Player) event.getWhoClicked();

        if (handleNavigationClick(event, slot)) {
            return;
        }

        if (slot == 0) {
            String origin = playerMenuUtility.getOriginCommand();
            if ("wtbmenu".equalsIgnoreCase(origin)) {
                player.performCommand("wtbmenu");
            } else if ("wtsmenu".equalsIgnoreCase(origin)) {
                player.performCommand("wtsmenu");
            }
            return;
        }

        if (event.getCurrentItem() == null || event.getCurrentItem().getType().equals(Material.AIR)) {
            return;
        }

        if (slot >= 9 && slot < 45) {
            int shopIndex = slot - 9;
            if (shopIndex >= 0 && shopIndex < currentPageShops.size()) {
                FoundShopItemModel foundShop = currentPageShops.get(shopIndex);

                // --- SHIFT CLICK: TOGGLE FAVORITE ---
                if (event.getClick() == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT
                        || event.getClick() == org.bukkit.event.inventory.ClickType.SHIFT_LEFT) {

                    CachedShopData cached = menuDataCache.get(foundShop);
                    String nearestWarp = (cached != null) ? cached.nearestWarp : getNearestWarpInfo(foundShop);

                    if (nearestWarp != null && !nearestWarp.isEmpty()
                            && !nearestWarp.equals(configProvider.NO_WARP_NEAR_SHOP_ERROR_MSG)) {

                        // 1. Run the command to update backend
                        player.performCommand("w favorite " + nearestWarp);

                        // 2. OPTIMISTIC UPDATE:
                        boolean currentStatus = (cached != null) && cached.isFav;
                        boolean newStatus = !currentStatus;

                        String ownerName = (cached != null) ? cached.ownerName : resolveOwnerName(foundShop.getShopOwner());

                        // IMPORTANT: Set timestamp to NOW to prevent double-checks
                        menuDataCache.put(foundShop, new CachedShopData(nearestWarp, newStatus, ownerName, System.currentTimeMillis()));

                        // 3. Mark as NOT sorted so the next render triggers a re-sort
                        hasBeenSorted = false;

                        // 4. Re-open immediately.
                        super.open(super.playerMenuUtility.getPlayerShopSearchResult());
                    } else {
                        super.open(super.playerMenuUtility.getPlayerShopSearchResult());
                    }
                    event.setCancelled(true);
                    return;
                }

                handleShopItemClick(event, player, foundShop);
            }
        }
    }

    private boolean handleNavigationClick(InventoryClickEvent event, int slot) {
        return switch (slot) {
            case 45 -> { handleMenuClickForNavToPrevPage(event); yield true; }
            case 46 -> { handleFirstPageClick(event); yield true; }
            case 52 -> { handleLastPageClick(event); yield true; }
            case 53 -> { handleMenuClickForNavToNextPage(event); yield true; }
            default -> false;
        };
    }

    private void handleShopItemClick(@NotNull InventoryClickEvent event, Player player, FoundShopItemModel foundShop) {
        if (configProvider.TP_PLAYER_DIRECTLY_TO_SHOP) {
            Location directShopLocation = foundShop.getShopLocation();
            handleDirectShopTeleport(player, directShopLocation);
            if (!shouldApplyTeleportDelay(player) && directShopLocation != null) {
                String colour = HighlightColourManager.getColour(player.getUniqueId());
                ShopHighlighter.highlightShop(directShopLocation, 600, FindItemAddOn.getInstance(), colour);
            }
        } else if (configProvider.TP_PLAYER_TO_NEAREST_WARP) {
            String nearestWarp = null;
            CachedShopData c = menuDataCache.get(foundShop);
            if (c != null) nearestWarp = c.nearestWarp();
            if (nearestWarp == null) nearestWarp = getNearestWarpInfo(foundShop);

            handleWarpTeleport(player, nearestWarp);
            Location warpShopLocation = foundShop.getShopLocation();
            if (warpShopLocation != null) {
                String colour = HighlightColourManager.getColour(player.getUniqueId());
                Bukkit.getScheduler().runTaskLater(
                        FindItemAddOn.getInstance(),
                        () -> ShopHighlighter.highlightShop(warpShopLocation, 600, FindItemAddOn.getInstance(), colour),
                        30L
                );
            }
        }
        handleCustomCommands(player, foundShop.getShopLocation());
        player.closeInventory();
    }

    private void handleDirectShopTeleport(@NotNull Player player, Location shopLocation) {
        if (!player.hasPermission(PlayerPermsEnum.FINDITEM_SHOPTP.value())) { sendNoPermissionMessage(player); return; }
        if (shopLocation == null) return;
        UUID shopOwner = ShopSearchActivityStorageUtil.getShopOwnerUUID(shopLocation);
        if (player.getUniqueId().equals(shopOwner) && !PlayerPermsEnum.canPlayerTpToOwnShop(player)) {
            player.sendMessage(ColorTranslator.translateColorCodes(configProvider.PLUGIN_PREFIX + configProvider.SHOP_TP_NO_PERMISSION_MSG));
            return;
        }
        Location locToTeleport = LocationUtils.findSafeLocationAroundShop(shopLocation, player);
        if (locToTeleport == null) { sendUnsafeAreaMessage(player); return; }
        ShopSearchActivityStorageUtil.addPlayerVisitEntryAsync(shopLocation, player);
        if (EssentialsXPlugin.isEnabled()) EssentialsXPlugin.setLastLocation(player);
        if (shouldApplyTeleportDelay(player)) {
            long delay = Long.parseLong(configProvider.TP_DELAY_IN_SECONDS);
            String tpDelayMsg = configProvider.TP_DELAY_MESSAGE;
            if (!StringUtils.isEmpty(tpDelayMsg)) {
                player.sendMessage(ColorTranslator.translateColorCodes(configProvider.PLUGIN_PREFIX + replaceDelayPlaceholder(tpDelayMsg, delay)));
            }
            Bukkit.getScheduler().scheduleSyncDelayedTask(FindItemAddOn.getInstance(), () -> {
                PaperLib.teleportAsync(player, locToTeleport, PlayerTeleportEvent.TeleportCause.PLUGIN);
                String colour = HighlightColourManager.getColour(player.getUniqueId());
                ShopHighlighter.highlightShop(shopLocation, 600, FindItemAddOn.getInstance(), colour);
            }, delay * 20);
        } else {
            PaperLib.teleportAsync(player, locToTeleport, PlayerTeleportEvent.TeleportCause.PLUGIN);
        }
    }

    private void handleWarpTeleport(Player player, String warpName) {
        switch (configProvider.NEAREST_WARP_MODE) {
            case 1 -> EssentialWarpsUtil.warpPlayer(player, warpName);
            case 2 -> PlayerWarpsUtil.executeWarpPlayer(player, warpName);
        }
    }

    private void handleCustomCommands(Player player, Location shopLocation) {
        if (configProvider.CUSTOM_CMDS_RUN_ENABLED && !configProvider.CUSTOM_CMDS_LIST.isEmpty() && shopLocation != null) {
            for (String cmd : configProvider.CUSTOM_CMDS_LIST) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), replaceCustomCmdPlaceholders(cmd, player, shopLocation));
            }
        }
    }

    private record CachedShopData(String nearestWarp, boolean isFav, String ownerName, long timestamp) {}

    @Override
    public void setMenuItems(List<FoundShopItemModel> foundShops) {
        if (loadingTask != null && !loadingTask.isCancelled()) {
            loadingTask.cancel();
        }

        currentRenderId++;
        final int taskRenderId = currentRenderId;

        ItemStack filler = new ItemStack(Material.ORANGE_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) { meta.setDisplayName(" "); filler.setItemMeta(meta); }
        for (int i = 1; i <= 8; i++) { inventory.setItem(i, filler); }
        addMenuBottomBar();
        updateNavButtonLabels(foundShops != null ? foundShops.size() : 0);

        if (foundShops == null || foundShops.isEmpty()) return;

        // Trigger Async Sort
        if (!hasBeenSorted && configProvider.NEAREST_WARP_MODE == 2 && PlayerWarpsPlugin.getIsEnabled()) {
            List<FoundShopItemModel> copyForSort = new ArrayList<>(foundShops);
            scanAndSortAsync(copyForSort);
        }

        int maxItemsPerPage = MAX_ITEMS_PER_PAGE;
        int startIndex = maxItemsPerPage * page;
        int endIndex = Math.min(startIndex + maxItemsPerPage, foundShops.size());

        if (startIndex >= foundShops.size()) {
            startIndex = 0;
            page = 0;
            endIndex = Math.min(maxItemsPerPage, foundShops.size());
        }

        List<FoundShopItemModel> pageItemsSafeCopy = new ArrayList<>(foundShops.subList(startIndex, endIndex));

        renderItems(pageItemsSafeCopy, startIndex, true);
        loadPageDataAsync(pageItemsSafeCopy, startIndex, taskRenderId);
    }

    private void scanAndSortAsync(List<FoundShopItemModel> shopsToSort) {
        Player viewer = playerMenuUtility.getOwner();

        loadingTask = Bukkit.getScheduler().runTaskAsynchronously(FindItemAddOn.getInstance(), () -> {
            boolean cacheUpdated = false;

            for (FoundShopItemModel shop : shopsToSort) {
                CachedShopData cached = menuDataCache.get(shop);

                boolean needsCheck = (cached == null);
                if (!needsCheck && cached.timestamp == 0L && !cached.isFav && configProvider.NEAREST_WARP_MODE == 2) {
                    needsCheck = true;
                }

                if (needsCheck) {
                    String nearestWarpName = (cached != null) ? cached.nearestWarp : getNearestWarpInfo(shop);
                    boolean isFav = false;

                    if (nearestWarpName != null
                            && !nearestWarpName.equals(configProvider.NO_WARP_NEAR_SHOP_ERROR_MSG)
                            && !nearestWarpName.equals(configProvider.NO_WG_REGION_NEAR_SHOP_ERROR_MSG)) {
                        isFav = fetchIsFavouriteSafe(viewer, nearestWarpName);
                    }

                    String ownerName = (cached != null) ? cached.ownerName : resolveOwnerName(shop.getShopOwner());

                    // Mark with current timestamp so we know it's verified
                    menuDataCache.put(shop, new CachedShopData(nearestWarpName, isFav, ownerName, System.currentTimeMillis()));
                    cacheUpdated = true;
                }
            }

            if (cacheUpdated || !hasBeenSorted) {
                shopsToSort.sort((s1, s2) -> {
                    CachedShopData c1 = menuDataCache.get(s1);
                    CachedShopData c2 = menuDataCache.get(s2);

                    boolean f1 = (c1 != null && c1.isFav);
                    boolean f2 = (c2 != null && c2.isFav);

                    // 1. Favorites always go first
                    int favComp = Boolean.compare(f2, f1);
                    if (favComp != 0) return favComp;

                    // 2. If selling (/wts), sort by highest price.
                    // If buying (/wtb), return 0 to preserve the random shuffle
                    if (!isBuying) {
                        return Double.compare(s2.getShopPrice(), s1.getShopPrice());
                    }

                    return 0;
                });

                hasBeenSorted = true;

                Bukkit.getScheduler().runTask(FindItemAddOn.getInstance(), () -> {
                    if (viewer.getOpenInventory().getTopInventory().equals(inventory)) {
                        playerMenuUtility.setPlayerShopSearchResult(shopsToSort);
                        setMenuItems(shopsToSort);
                    }
                });
            }
        });
    }

    private void loadPageDataAsync(List<FoundShopItemModel> pageItems, int startIndex, int renderId) {
        Bukkit.getScheduler().runTaskAsynchronously(FindItemAddOn.getInstance(), () -> {
            boolean updateNeeded = false;
            for (FoundShopItemModel shop : pageItems) {
                if (!menuDataCache.containsKey(shop)) {
                    String warp = getNearestWarpInfo(shop);
                    String owner = resolveOwnerName(shop.getShopOwner());
                    // IMPORTANT: Use 0L timestamp to indicate "Placeholder / Untrusted"
                    menuDataCache.put(shop, new CachedShopData(warp, false, owner, 0L));
                    updateNeeded = true;
                }
            }

            if (updateNeeded) {
                Bukkit.getScheduler().runTask(FindItemAddOn.getInstance(), () -> {
                    if (currentRenderId == renderId && playerMenuUtility.getOwner().getOpenInventory().getTopInventory() == inventory) {
                        renderItems(pageItems, startIndex, false);
                    }
                });
            }
        });
    }

    private boolean fetchIsFavouriteSafe(Player player, String warpName) {
        AtomicBoolean result = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        try {
            PlayerWarpsPlugin.isFavourite(player, warpName, isFav -> {
                result.set(isFav);
                latch.countDown();
            });
            latch.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // Ignore
        }

        return result.get();
    }

    private void renderItems(List<FoundShopItemModel> pageItems, int startIndex, boolean usePartialData) {
        int shopItemSlot = 9;

        currentPageShops.clear();

        for (int i = 9; i < 45; i++) {
            if (i >= 9 + pageItems.size()) {
                inventory.setItem(i, new ItemStack(Material.AIR));
            }
        }

        for (FoundShopItemModel foundShop : pageItems) {
            if (shopItemSlot >= 45) break;

            CachedShopData data = menuDataCache.get(foundShop);

            String cachedWarp = (data != null) ? data.nearestWarp : (usePartialData ? "Checking..." : null);
            boolean cachedIsFav = (data != null) ? data.isFav : false;
            String cachedOwner = (data != null) ? data.ownerName : (usePartialData ? "Loading..." : "Unknown");

            ItemStack item = createShopItem(foundShop, cachedWarp, cachedIsFav, cachedOwner);

            if (cachedIsFav) {
                item = ensureFavouriteLoreAndGlow(item, true);
            }

            inventory.setItem(shopItemSlot, item);
            currentPageShops.add(foundShop);
            shopItemSlot++;
        }
    }

    private String resolveOwnerName(UUID ownerId) {
        if (ownerId == null) return "Admin";
        if (ownerNameCache.containsKey(ownerId)) return ownerNameCache.get(ownerId);

        String name = "Unknown";
        OfflinePlayer op = Bukkit.getOfflinePlayer(ownerId);
        if (op.getName() != null) name = op.getName();

        ownerNameCache.put(ownerId, name);
        return name;
    }

    private void setButtonName(int slot, String name) {
        ItemStack btn = inventory.getItem(slot);
        if (btn == null || btn.getType() == Material.AIR) return;
        ItemMeta m = btn.getItemMeta();
        if (m == null) return;
        m.setDisplayName(ColorTranslator.translateColorCodes(name));
        btn.setItemMeta(m);
        inventory.setItem(slot, btn);
    }

    private void updateNavButtonLabels(int totalItems) {
        int totalPages = Math.max(1, (int) Math.ceil(totalItems / (double) MAX_ITEMS_PER_PAGE));
        int currentPage = page + 1;
        int prevPage = Math.max(1, currentPage - 1);
        int nextPage = Math.min(totalPages, currentPage + 1);
        setButtonName(45, "&6« Prev &7(" + prevPage + "/" + totalPages + ")");
        setButtonName(46, "&6First &7(1/" + totalPages + ")");
        setButtonName(52, "&6Last &7(" + totalPages + "/" + totalPages + ")");
        setButtonName(53, "&6Next » &7(" + nextPage + "/" + totalPages + ")");
    }

    private @NotNull ItemStack createShopItem(@NotNull FoundShopItemModel foundShop, String cachedWarp, boolean cachedIsFav, String cachedOwnerName) {
        ItemStack item = new ItemStack(foundShop.getItem().getType(), foundShop.getItem().getAmount());
        ItemMeta meta = foundShop.getItem().getItemMeta();
        if (meta == null) {
            meta = Bukkit.getItemFactory().getItemMeta(item.getType());
        }

        List<String> lore = new ArrayList<>();
        addItemLore(lore, foundShop, cachedWarp, cachedIsFav, cachedOwnerName);
        meta.setLore(lore);

        setLocationData(meta, foundShop, cachedWarp);

        if (foundShop.getItem().getItemMeta().hasCustomModelData()) {
            meta.setCustomModelData(foundShop.getItem().getItemMeta().getCustomModelData());
        }

        item.setItemMeta(meta);
        return item;
    }

    private void addItemLore(List<String> lore, FoundShopItemModel foundShop, String cachedWarp, boolean cachedIsFav, String cachedOwnerName) {
        ItemMeta shopItemMeta = foundShop.getItem().getItemMeta();
        if (shopItemMeta != null && shopItemMeta.hasLore()) {
            for (String line : shopItemMeta.getLore()) {
                lore.add(ColorTranslator.translateColorCodes(line));
            }
        }

        List<String> loreTemplate;
        if (isBuying && configProvider.SHOP_GUI_ITEM_LORE_BUY != null) {
            loreTemplate = configProvider.SHOP_GUI_ITEM_LORE_BUY;
        } else if (!isBuying && configProvider.SHOP_GUI_ITEM_LORE_SELL != null) {
            loreTemplate = configProvider.SHOP_GUI_ITEM_LORE_SELL;
        } else {
            loreTemplate = configProvider.SHOP_GUI_ITEM_LORE;
        }

        if (cachedIsFav && !hasFavouriteLore(lore)) {
            lore.add(0, ColorTranslator.translateColorCodes("&6⭐ &7Favourited Warp"));
        }

        for (String loreLine : loreTemplate) {
            String processed;
            if (loreLine.contains(ShopLorePlaceholdersEnum.NEAREST_WARP.value())) {
                String warpDisplay = (cachedWarp != null) ? cachedWarp : configProvider.NO_WARP_NEAR_SHOP_ERROR_MSG;
                processed = loreLine.replace(ShopLorePlaceholdersEnum.NEAREST_WARP.value(), warpDisplay);
            } else {
                processed = replaceLorePlaceholders(loreLine, foundShop, cachedWarp, cachedOwnerName);
            }

            if (processed.contains("{FAV_TOGGLE}") || processed.toLowerCase(Locale.ROOT).contains("(un)favourite")) {
                String toggleWord = cachedIsFav ? "&cunfavourite" : "&afavourite";
                processed = processed
                        .replace("{FAV_TOGGLE}", toggleWord)
                        .replace("(un)favourite", toggleWord);
            }

            lore.add(ColorTranslator.translateColorCodes(processed));
        }

        if (configProvider.TP_PLAYER_DIRECTLY_TO_SHOP
                && playerMenuUtility.getOwner().hasPermission(PlayerPermsEnum.FINDITEM_SHOPTP.value())) {
            lore.add(ColorTranslator.translateColorCodes(configProvider.CLICK_TO_TELEPORT_MSG));
        }
    }

    private boolean hasFavouriteLore(List<String> lore) {
        if (lore == null) return false;
        for (String s : lore) {
            String stripped = org.bukkit.ChatColor.stripColor(s == null ? "" : s);
            if (stripped != null && stripped.toLowerCase(Locale.ROOT).contains("favourite")) return true;
        }
        return false;
    }

    private ItemStack ensureFavouriteLoreAndGlow(ItemStack base, boolean alreadyHasFavLore) {
        ItemStack item = base.clone();
        ItemMeta im = item.getItemMeta();
        if (im == null) return base;

        if (!alreadyHasFavLore) {
            List<String> lore = im.hasLore() ? new ArrayList<>(im.getLore()) : new ArrayList<>();
            if (!hasFavouriteLore(lore)) {
                lore.add(0, ColorTranslator.translateColorCodes("&6⭐ &7Favourite"));
            }
            im.setLore(lore);
            item.setItemMeta(im);
        }
        return makeItemGlow(item);
    }

    private ItemStack makeItemGlow(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Enchantment durability = org.bukkit.enchantments.Enchantment.getByName("DURABILITY");
            if (durability != null) meta.addEnchant(durability, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String getNearestWarpInfo(FoundShopItemModel foundShop) {
        int nearestWarpMode = configProvider.NEAREST_WARP_MODE;
        Player viewingPlayer = playerMenuUtility.getOwner();
        switch (nearestWarpMode) {
            case 1:
                if (EssentialsXPlugin.isEnabled()) {
                    String nearestEWarp = EssentialWarpsUtil.findNearestWarp(foundShop.getShopLocation());
                    return (nearestEWarp != null && !StringUtils.isEmpty(nearestEWarp)) ? nearestEWarp
                            : configProvider.NO_WARP_NEAR_SHOP_ERROR_MSG;
                }
                break;
            case 2:
                if (PlayerWarpsPlugin.getIsEnabled()) {
                    Warp nearestPlayerWarp = PlayerWarpsUtil.findNearestWarp(
                            foundShop.getShopLocation(),
                            viewingPlayer,
                            foundShop.getShopOwner()
                    );
                    return (nearestPlayerWarp != null) ? nearestPlayerWarp.getWarpName()
                            : configProvider.NO_WARP_NEAR_SHOP_ERROR_MSG;
                }
                break;
            case 3:
                if (WGPlugin.isEnabled()) {
                    String nearestWGRegion = new WGRegionUtils().findNearestWGRegion(foundShop.getShopLocation());
                    return (nearestWGRegion != null && !StringUtils.isEmpty(nearestWGRegion)) ? nearestWGRegion
                            : configProvider.NO_WG_REGION_NEAR_SHOP_ERROR_MSG;
                }
                break;
            default:
                Logger.logDebugInfo("Invalid value in 'nearest-warp-mode' in config.yml!");
        }
        return configProvider.NO_WARP_NEAR_SHOP_ERROR_MSG;
    }

    private void setLocationData(ItemMeta meta, FoundShopItemModel foundShop, String cachedWarp) {
        NamespacedKey key = new NamespacedKey(FindItemAddOn.getInstance(), NAMEDSPACE_KEY_LOCATION_DATA);
        String locData = "";
        if (configProvider.TP_PLAYER_DIRECTLY_TO_SHOP) {
            Location shopLoc = foundShop.getShopLocation();
            locData = String.format("%s,%d,%d,%d", shopLoc.getWorld().getName(), shopLoc.getBlockX(),
                    shopLoc.getBlockY(), shopLoc.getBlockZ());
        } else if (configProvider.TP_PLAYER_TO_NEAREST_WARP) {
            locData = (cachedWarp != null) ? cachedWarp : getNearestWarpInfo(foundShop);
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, locData);
    }

    private @NotNull String replaceLorePlaceholders(String text, @NotNull FoundShopItemModel shop, String cachedWarp, String cachedOwnerName) {
        String result = text.replace(ShopLorePlaceholdersEnum.ITEM_PRICE.value(), formatNumber(shop.getShopPrice()));

        if (result.contains(ShopLorePlaceholdersEnum.SHOP_STOCK.value())) {
            int stock = shop.getRemainingStockOrSpace();
            String stockText;
            if (stock == -2) {
                int stockOrSpace = processUnknownStockSpace(shop);
                stockText = (stockOrSpace == -2) ? SHOP_STOCK_UNKNOWN
                        : (stockOrSpace == -1 ? SHOP_STOCK_UNLIMITED : String.valueOf(stockOrSpace));
            } else {
                stockText = (stock == Integer.MAX_VALUE) ? SHOP_STOCK_UNLIMITED : String.valueOf(stock);
            }
            result = result.replace(ShopLorePlaceholdersEnum.SHOP_STOCK.value(), stockText);
        }

        result = result.replace(ShopLorePlaceholdersEnum.SHOP_PER_ITEM_QTY.value(),
                String.valueOf(shop.getItem().getAmount()));

        if (result.contains(ShopLorePlaceholdersEnum.SHOP_OWNER.value())) {
            result = result.replace(ShopLorePlaceholdersEnum.SHOP_OWNER.value(), cachedOwnerName);
        }

        if (result.contains(ShopLorePlaceholdersEnum.SHOP_LOCATION.value())) {
            Location loc = shop.getShopLocation();
            String locText = loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
            result = result.replace(ShopLorePlaceholdersEnum.SHOP_LOCATION.value(), locText);
        }

        result = result.replace(ShopLorePlaceholdersEnum.SHOP_WORLD.value(),
                Objects.requireNonNull(shop.getShopLocation().getWorld()).getName());

        // --- NEW PW API VISIT FETCHING ---
        String visitsText = "0";
        if (cachedWarp != null && !cachedWarp.equals(configProvider.NO_WARP_NEAR_SHOP_ERROR_MSG) && configProvider.NEAREST_WARP_MODE == 2) {
            // Fetch directly from the PlayerWarps cached list to avoid async API callbacks inside the lore builder
            Warp pwWarp = PlayerWarpsPlugin.getAllWarps().stream()
                    .filter(w -> w.getWarpName().equalsIgnoreCase(cachedWarp))
                    .findFirst()
                    .orElse(null);

            if (pwWarp != null) {
                // Gets the visit count straight from the PlayerWarps JSON data
                visitsText = String.valueOf(pwWarp.getWarpVisit().getWarpVisits());
            }
        } else {
            // Fallback to your internal tracker if PlayerWarps isn't being used
            visitsText = String.valueOf(ShopSearchActivityStorageUtil.getPlayerVisitCount(shop.getShopLocation()));
        }

        result = result.replace(ShopLorePlaceholdersEnum.SHOP_VISITS.value(), visitsText);

        if (result.contains(ShopLorePlaceholdersEnum.WARP_AVG_RATING.value())
                || result.contains(ShopLorePlaceholdersEnum.WARP_TOTAL_RATINGS.value())) {
            String warpName = cachedWarp;
            if (warpName == null && PlayerWarpsPlugin.getIsEnabled() && FindItemAddOn.getConfigProvider().NEAREST_WARP_MODE == 2) {
                warpName = getNearestWarpInfo(shop);
                if (warpName.equals(configProvider.NO_WARP_NEAR_SHOP_ERROR_MSG)) warpName = null;
            }

            double avg = (warpName != null) ? PlayerWarpsPlugin.getWarpAverageRating(warpName) : -1;
            int total = (warpName != null) ? PlayerWarpsPlugin.getWarpTotalRatings(warpName) : -1;
            String avgString = avg >= 0 ? String.format("%.2f", avg) : "N/A";
            String totalString = total >= 0 ? String.valueOf(total) : "N/A";
            result = result.replace(ShopLorePlaceholdersEnum.WARP_AVG_RATING.value(), avgString)
                    .replace(ShopLorePlaceholdersEnum.WARP_TOTAL_RATINGS.value(), "(" + totalString + " ratings)");
        }

        return ColorTranslator.translateColorCodes(result);
    }

    private int processUnknownStockSpace(FoundShopItemModel shop) {
        return FindItemAddOn.getQsApiInstance().processUnknownStockSpace(shop.getShopLocation(), shop.isToBuy());
    }

    private String replaceDelayPlaceholder(String tpDelayMsg, long delay) {
        return tpDelayMsg.replace("{DELAY}", String.valueOf(delay));
    }

    private String formatNumber(double number) {
        if (configProvider.SHOP_GUI_USE_SHORTER_CURRENCY_FORMAT) {
            if (number < 100_000) {
                return String.format("%,.2f", number);
            } else if (number < 1_000_000) {
                return String.format("%.2fK", number / 1_000.0);
            } else if (number < 1_000_000_000) {
                return String.format("%.2fM", number / 1_000_000.0);
            } else if (number < 1_000_000_000_000L) {
                return String.format("%.2fB", number / 1_000_000_000.0);
            } else {
                return String.format("%.2fT", number / 1_000_000_000_000.0);
            }
        } else {
            return String.format("%,.2f", number);
        }
    }

    private String replaceCustomCmdPlaceholders(String cmd, Player player, Location shopLoc) {
        return cmd
                .replace(CustomCmdPlaceholdersEnum.PLAYER_NAME.value(), player.getName())
                .replace(CustomCmdPlaceholdersEnum.SHOP_LOC_X.value(), Double.toString(shopLoc.getX()))
                .replace(CustomCmdPlaceholdersEnum.SHOP_LOC_Y.value(), Double.toString(shopLoc.getY()))
                .replace(CustomCmdPlaceholdersEnum.SHOP_LOC_Z.value(), Double.toString(shopLoc.getZ()));
    }

    private void sendNoPermissionMessage(Player player) {
        if (!StringUtils.isEmpty(configProvider.SHOP_TP_NO_PERMISSION_MSG)) {
            player.sendMessage(ColorTranslator.translateColorCodes(configProvider.PLUGIN_PREFIX
                    + configProvider.SHOP_TP_NO_PERMISSION_MSG));
        }
    }

    private void sendUnsafeAreaMessage(Player player) {
        if (!StringUtils.isEmpty(configProvider.UNSAFE_SHOP_AREA_MSG)) {
            player.sendMessage(ColorTranslator.translateColorCodes(configProvider.PLUGIN_PREFIX
                    + configProvider.UNSAFE_SHOP_AREA_MSG));
        }
    }

    private boolean shouldApplyTeleportDelay(Player player) {
        return StringUtils.isNumeric(configProvider.TP_DELAY_IN_SECONDS)
                && !"0".equals(configProvider.TP_DELAY_IN_SECONDS)
                && !PlayerPermsEnum.hasShopTpDelayBypassPermOrAdmin(player);
    }

    // --- Navigation Handlers ---
    private void handleMenuClickForNavToNextPage(InventoryClickEvent event) {
        if (!((index + 1) >= super.playerMenuUtility.getPlayerShopSearchResult().size())) {
            page = page + 1;
            super.open(super.playerMenuUtility.getPlayerShopSearchResult());
        } else {
            if (!StringUtils.isEmpty(configProvider.SHOP_NAV_LAST_PAGE_ALERT_MSG)) {
                event.getWhoClicked().sendMessage(ColorTranslator.translateColorCodes(configProvider.PLUGIN_PREFIX + configProvider.SHOP_NAV_LAST_PAGE_ALERT_MSG));
            }
        }
    }
    private void handleMenuClickForNavToPrevPage(InventoryClickEvent event) {
        if (page == 0) {
            if (!StringUtils.isEmpty(configProvider.SHOP_NAV_FIRST_PAGE_ALERT_MSG)) {
                event.getWhoClicked().sendMessage(ColorTranslator.translateColorCodes(configProvider.PLUGIN_PREFIX + configProvider.SHOP_NAV_FIRST_PAGE_ALERT_MSG));
            }
        } else {
            page = page - 1;
            super.open(super.playerMenuUtility.getPlayerShopSearchResult());
        }
    }
    private void handleFirstPageClick(InventoryClickEvent event) {
        if (page == 0) {
            if (!StringUtils.isEmpty(configProvider.SHOP_NAV_FIRST_PAGE_ALERT_MSG)) {
                event.getWhoClicked().sendMessage(ColorTranslator.translateColorCodes(configProvider.PLUGIN_PREFIX + configProvider.SHOP_NAV_FIRST_PAGE_ALERT_MSG));
            }
        } else {
            page = 0;
            super.open(super.playerMenuUtility.getPlayerShopSearchResult());
        }
    }
    private void handleLastPageClick(InventoryClickEvent event) {
        int listSize = super.playerMenuUtility.getPlayerShopSearchResult().size();
        if (!((index + 1) >= listSize)) {
            page = Math.max(0, (listSize - 1) / MAX_ITEMS_PER_PAGE);
            super.open(super.playerMenuUtility.getPlayerShopSearchResult());
        } else {
            if (!StringUtils.isEmpty(configProvider.SHOP_NAV_LAST_PAGE_ALERT_MSG)) {
                event.getWhoClicked().sendMessage(ColorTranslator.translateColorCodes(configProvider.PLUGIN_PREFIX + configProvider.SHOP_NAV_LAST_PAGE_ALERT_MSG));
            }
        }
    }
}