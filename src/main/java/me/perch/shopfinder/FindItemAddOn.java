package me.perch.shopfinder;

import me.perch.shopfinder.commands.*;
import me.perch.shopfinder.commands.simpapi.BuySubCmd;
import me.perch.shopfinder.commands.simpapi.HideShopSubCmd;
import me.perch.shopfinder.commands.simpapi.ReloadSubCmd;
import me.perch.shopfinder.commands.simpapi.RevealShopSubCmd;
import me.perch.shopfinder.commands.simpapi.SellSubCmd;
import me.perch.shopfinder.config.ConfigProvider;
import me.perch.shopfinder.config.ConfigSetup;
import me.perch.shopfinder.dependencies.EssentialsXPlugin;
import me.perch.shopfinder.dependencies.PlayerWarpsPlugin;
import me.perch.shopfinder.dependencies.WGPlugin;
import me.perch.shopfinder.handlers.command.CmdExecutorHandler;
import me.perch.shopfinder.handlers.gui.PlayerMenuUtility;
import me.perch.shopfinder.listeners.MenuListener;
import me.perch.shopfinder.listeners.PWPlayerWarpCreateEventListener;
import me.perch.shopfinder.listeners.PWPlayerWarpRemoveEventListener;
import me.perch.shopfinder.listeners.PlayerCommandSendEventListener;
import me.perch.shopfinder.listeners.PlayerJoinEventListener;
import me.perch.shopfinder.listeners.PluginEnableEventListener;
import me.perch.shopfinder.papi.PapiRegistrar; // <-- added
import me.perch.shopfinder.quickshop.QSApi;
import me.perch.shopfinder.quickshop.impl.QSHikariAPIHandler;
import me.perch.shopfinder.scheduledtasks.Task15MinInterval;
import me.perch.shopfinder.utils.HighlightColourManager;
import me.perch.shopfinder.utils.enums.PlayerPermsEnum;
import me.perch.shopfinder.utils.json.ShopSearchActivityStorageUtil;
import me.perch.shopfinder.utils.log.Logger;
import me.perch.shopfinder.utils.UpdateChecker;
import me.perch.shopfinder.utils.ShopHighlighter;
import me.perch.shopfinder.utils.ExcludedWarpsUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import me.kodysimpson.simpapi.colors.ColorTranslator;
import me.kodysimpson.simpapi.command.CommandManager;
import me.kodysimpson.simpapi.command.SubCommand;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import com.ghostchu.quickshop.QuickShop;
import com.ghostchu.quickshop.api.shop.Shop;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

@Slf4j
public final class FindItemAddOn extends JavaPlugin {

    // ONLY FOR SNAPSHOT BUILDS
    private static final boolean ENABLE_TRIAL_PERIOD = false;
    private static final int TRIAL_END_YEAR = 2024, TRIAL_END_MONTH = 5, TRIAL_END_DAY = 5;

    private static Plugin pluginInstance;
    public FindItemAddOn() { pluginInstance = this; }
    public static Plugin getInstance() { return pluginInstance; }
    public static String serverVersion;
    private static final int BS_PLUGIN_METRIC_ID = 12382;
    private static final int SPIGOT_PLUGIN_ID = 95104;
    private static final int REPEATING_TASK_SCHEDULE_MINS = 15*60*20; // 15 minutes in ticks

    @Getter
    private static ConfigProvider configProvider;
    @Getter
    private static UpdateChecker updateChecker;

    private static boolean isPluginOutdated = false;
    private static boolean qSReremakeInstalled = false;
    private static boolean qSHikariInstalled = false;
    private static QSApi<QuickShop, Shop> qsApi;

    private static final HashMap<Player, PlayerMenuUtility> playerMenuUtilityMap = new HashMap<>();

    private CmdExecutorHandler cmdExecutorHandler;

    // --- added: dynamic PAPI registrar so placeholders survive PlugMan/PAPI reloads
    private PapiRegistrar papiRegistrar;

    @Override
    public void onLoad() {
        Logger.logInfo("A Shop Search AddOn for QuickShop developed by myzticbean");

        if (this.getDescription().getVersion().toLowerCase().contains("snapshot")) {
            Logger.logWarning("This is a SNAPSHOT build! NOT recommended for production servers.");
            Logger.logWarning("If you find any bugs, please report them here: https://github.com/myzticbean/QSFindItemAddOn/issues");
        }
    }

    @Override
    public void onEnable() {
        // Data & managers
        HighlightColourManager.init(getDataFolder());
        ExcludedWarpsUtil.init(this);

        // Register Bukkit listeners
        initBukkitEventListeners();

        // Highlighter system
        ShopHighlighter.init(this);

        // Config lifecycle
        this.saveDefaultConfig();
        this.getConfig().options().copyDefaults(true);
        ConfigSetup.setupConfig();
        ConfigSetup.get().options().copyDefaults(true);
        ConfigSetup.checkForMissingProperties();
        ConfigSetup.saveConfig();
        initConfigProvider();
        ConfigSetup.copySampleConfig();

        // Commands
        this.cmdExecutorHandler = new CmdExecutorHandler();
        getCommand("outofstock").setExecutor(new OutOfStockCommand(cmdExecutorHandler));
        initCommands();

        getCommand("wheretobuy").setExecutor(new WhereToBuyCommand());
        getCommand("wheretobuy").setTabCompleter(new ShopSearchTabCompleter());
        getCommand("highlightcolour").setExecutor(new HighlightColourCommand());
        getCommand("wheretoget").setExecutor(new WhereToGetCommand());
        getCommand("wheretoget").setTabCompleter(new ShopSearchTabCompleter());
        getCommand("excludewarp").setExecutor(new ExcludeWarpCommand());
        getCommand("excludewarp").setTabCompleter(new ExcludeWarpCommand());
        getCommand("includewarp").setExecutor(new ExcludeWarpCommand());
        getCommand("includewarp").setTabCompleter(new ExcludeWarpCommand());
        getCommand("wheretosell").setExecutor(new WhereToSellCommand());
        getCommand("wheretosell").setTabCompleter(new ShopSearchTabCompleter());

        // QuickShop presence detection
        if (!Bukkit.getPluginManager().isPluginEnabled("QuickShop")
                && !Bukkit.getPluginManager().isPluginEnabled("QuickShop-Hikari")) {
            Logger.logInfo("Delaying QuickShop hook as they are not enabled yet");
        } else if (Bukkit.getPluginManager().isPluginEnabled("QuickShop")) {
            qSReremakeInstalled = true;
        } else {
            qSHikariInstalled = true;
        }

        // --- PAPI dynamic registration (works with PlugMan & /papi reload)
        papiRegistrar = new PapiRegistrar(this);
        getServer().getPluginManager().registerEvents(papiRegistrar, this);
        papiRegistrar.tryRegister(); // register immediately if PAPI is already present

        // Defer the heavy startup bits until the server has finished enabling plugins
        Bukkit.getScheduler().scheduleSyncDelayedTask(FindItemAddOn.getInstance(), this::runPluginStartupTasks);

        // Trial build gate (kept as-is)
        if (ENABLE_TRIAL_PERIOD) {
            Logger.logWarning("THIS IS A TRIAL BUILD!");
            LocalDateTime trialEndDate = LocalDate.of(TRIAL_END_YEAR, TRIAL_END_MONTH, TRIAL_END_DAY).atTime(LocalTime.MIDNIGHT);
            LocalDateTime today = LocalDateTime.now();
            Duration duration = Duration.between(trialEndDate, today);
            boolean hasPassed = Duration.ofDays(ChronoUnit.DAYS.between(today, trialEndDate)).isNegative();
            if (hasPassed) {
                Logger.logError("Your trial has expired! Please contact the developer.");
                getServer().getPluginManager().disablePlugin(this);
            } else {
                Logger.logWarning("You have " + Math.abs(duration.toDays()) + " days remaining in your trial.");
            }
        }
    }

    @Override
    public void onDisable() {
        // Unregister PAPI expansion cleanly
        if (papiRegistrar != null) {
            papiRegistrar.unregister();
        }

        if (qsApi != null) {
            ShopSearchActivityStorageUtil.saveShopsToFile();
        } else if (!ENABLE_TRIAL_PERIOD) {
            Logger.logError("Uh oh! Looks like either this plugin has crashed or you don't have QuickShop-Hikari or QuickShop-Reremake installed.");
        }
        Logger.logInfo("Bye!");
    }

    private void runPluginStartupTasks() {
        serverVersion = Bukkit.getServer().getVersion();
        Logger.logInfo("Server version found: " + serverVersion);

        if (!isQSReremakeInstalled() && !isQSHikariInstalled()) {
            Logger.logError("QuickShop is required to use this addon. Please install QuickShop and try again!");
            Logger.logError("Both QuickShop-Hikari and QuickShop-Reremake are supported by this addon.");
            Logger.logError("Download links:");
            Logger.logError("» QuickShop-Hikari: https://www.spigotmc.org/resources/100125");
            Logger.logError("» QuickShop-Reremake (Support ending soon): https://www.spigotmc.org/resources/62575");
            getServer().getPluginManager().disablePlugin(this);
            return;
        } else {
            Logger.logInfo("Found QuickShop-Hikari");
            qsApi = new QSHikariAPIHandler();
            qsApi.registerSubCommand();
        }

        ShopSearchActivityStorageUtil.loadShopsFromFile();
        ShopSearchActivityStorageUtil.migrateHiddenShopsToShopsJson();

        PlayerWarpsPlugin.setup();
        EssentialsXPlugin.setup();
        WGPlugin.setup();

        initExternalPluginEventListeners();

        Logger.logInfo("Registering tasks");
        Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(this, new Task15MinInterval(), 0, REPEATING_TASK_SCHEDULE_MINS);
    }

    private void initCommands() {
        Logger.logInfo("Registering commands");
        initFindItemCmd();
        initFindItemAdminCmd();
    }

    private void initBukkitEventListeners() {
        Logger.logInfo("Registering Bukkit event listeners");
        this.getServer().getPluginManager().registerEvents(new PluginEnableEventListener(), this);
        this.getServer().getPluginManager().registerEvents(new PlayerCommandSendEventListener(), this);
        this.getServer().getPluginManager().registerEvents(new MenuListener(), this);
        this.getServer().getPluginManager().registerEvents(new PlayerJoinEventListener(), this);
    }

    private void initExternalPluginEventListeners() {
        Logger.logInfo("Registering external plugin event listeners");
        if (PlayerWarpsPlugin.getIsEnabled()) {
            this.getServer().getPluginManager().registerEvents(new PWPlayerWarpRemoveEventListener(), this);
            this.getServer().getPluginManager().registerEvents(new PWPlayerWarpCreateEventListener(), this);
        }
    }

    public static void initConfigProvider() {
        configProvider = new ConfigProvider();
    }

    public static PlayerMenuUtility getPlayerMenuUtility(Player p) {
        PlayerMenuUtility playerMenuUtility;
        if (playerMenuUtilityMap.containsKey(p)) {
            return playerMenuUtilityMap.get(p);
        } else {
            playerMenuUtility = new PlayerMenuUtility(p);
            playerMenuUtilityMap.put(p, playerMenuUtility);
            return playerMenuUtility;
        }
    }

    public static boolean getPluginOutdated() {
        return isPluginOutdated;
    }

    public static int getPluginID() {
        return SPIGOT_PLUGIN_ID;
    }

    private void initFindItemCmd() {
        List<String> alias;
        if (StringUtils.isEmpty(FindItemAddOn.getConfigProvider().FIND_ITEM_TO_SELL_AUTOCOMPLETE)
                || StringUtils.containsIgnoreCase(FindItemAddOn.getConfigProvider().FIND_ITEM_TO_SELL_AUTOCOMPLETE, " ")) {
            alias = Arrays.asList("shopsearch", "searchshop", "searchitem");
        } else {
            alias = FindItemAddOn.getConfigProvider().FIND_ITEM_COMMAND_ALIAS;
        }

        Class<? extends SubCommand>[] subCommands;
        if (FindItemAddOn.getConfigProvider().FIND_ITEM_CMD_REMOVE_HIDE_REVEAL_SUBCMDS) {
            subCommands = new Class[]{
                    SellSubCmd.class, BuySubCmd.class
            };
        } else {
            subCommands = new Class[]{
                    SellSubCmd.class, BuySubCmd.class, HideShopSubCmd.class, RevealShopSubCmd.class
            };
        }
        try {
            CommandManager.createCoreCommand(
                    this,
                    "finditem",
                    "Search for items from all shops using an interactive GUI",
                    "/finditem",
                    (commandSender, subCommandList) -> {
                        commandSender.sendMessage(ColorTranslator.translateColorCodes(""));
                        commandSender.sendMessage(ColorTranslator.translateColorCodes("&7------------------------"));
                        commandSender.sendMessage(ColorTranslator.translateColorCodes("&6&lShop Search Commands"));
                        commandSender.sendMessage(ColorTranslator.translateColorCodes("&7------------------------"));
                        for (SubCommand subCommand : subCommandList) {
                            commandSender.sendMessage(ColorTranslator.translateColorCodes("&#ff9933" + subCommand.getSyntax() + " &#a3a3c2" + subCommand.getDescription()));
                        }
                        commandSender.sendMessage(ColorTranslator.translateColorCodes(""));
                        commandSender.sendMessage(ColorTranslator.translateColorCodes("&#b3b300Command alias:"));
                        alias.forEach(alias_i -> commandSender.sendMessage(ColorTranslator.translateColorCodes("&8&l» &#2db300/" + alias_i)));
                        commandSender.sendMessage(ColorTranslator.translateColorCodes(""));
                    },
                    alias,
                    subCommands);
            Logger.logInfo("Registered /finditem command");
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Logger.logError(e);
        }
    }

    private void initFindItemAdminCmd() {
        List<String> alias = List.of("fiadmin");
        try {
            CommandManager.createCoreCommand(
                    this,
                    "finditemadmin",
                    "Admin command for Shop Search addon",
                    "/finditemadmin",
                    (commandSender, subCommandList) -> {
                        if (
                                (commandSender.isOp())
                                        || (!commandSender.isOp() && (commandSender.hasPermission(PlayerPermsEnum.FINDITEM_ADMIN.value())
                                        || commandSender.hasPermission(PlayerPermsEnum.FINDITEM_RELOAD.value())))
                        ) {
                            commandSender.sendMessage(ColorTranslator.translateColorCodes(""));
                            commandSender.sendMessage(ColorTranslator.translateColorCodes("&7-----------------------------"));
                            commandSender.sendMessage(ColorTranslator.translateColorCodes("&6&lShop Search Admin Commands"));
                            commandSender.sendMessage(ColorTranslator.translateColorCodes("&7-----------------------------"));

                            for (SubCommand subCommand : subCommandList) {
                                commandSender.sendMessage(ColorTranslator.translateColorCodes("&#ff1a1a" + subCommand.getSyntax() + " &#a3a3c2" + subCommand.getDescription()));
                            }
                            commandSender.sendMessage(ColorTranslator.translateColorCodes(""));
                            commandSender.sendMessage(ColorTranslator.translateColorCodes("&#b3b300Command alias:"));
                            alias.forEach(alias_i -> commandSender.sendMessage(ColorTranslator.translateColorCodes("&8&l» &#2db300/" + alias_i)));
                            commandSender.sendMessage(ColorTranslator.translateColorCodes(""));
                        }
                    },
                    alias,
                    ReloadSubCmd.class);
            Logger.logInfo("Registered /finditemadmin command");
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Logger.logError(e);
        }
    }

    public static boolean isQSReremakeInstalled() {
        return qSReremakeInstalled;
    }

    public static boolean isQSHikariInstalled() {
        return qSHikariInstalled;
    }

    public static void setQSReremakeInstalled(boolean qSReremakeInstalled) {
        FindItemAddOn.qSReremakeInstalled = qSReremakeInstalled;
    }

    public static void setQSHikariInstalled(boolean qSHikariInstalled) {
        FindItemAddOn.qSHikariInstalled = qSHikariInstalled;
    }

    public static QSApi<QuickShop, Shop> getQsApiInstance() {
        return qsApi;
    }
}
