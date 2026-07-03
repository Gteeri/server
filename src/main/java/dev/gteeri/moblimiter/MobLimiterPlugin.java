package dev.gteeri.moblimiter;

import dev.gteeri.moblimiter.command.MobLimitCommand;
import dev.gteeri.moblimiter.config.PluginConfig;
import dev.gteeri.moblimiter.freeze.ClusterScanner;
import dev.gteeri.moblimiter.freeze.FreezeListener;
import dev.gteeri.moblimiter.freeze.FreezeManager;
import dev.gteeri.moblimiter.gui.ConfigGui;
import dev.gteeri.moblimiter.gui.MainMenuGui;
import dev.gteeri.moblimiter.gui.StatsGui;
import dev.gteeri.moblimiter.limits.LimitListener;
import dev.gteeri.moblimiter.limits.LimitService;
import dev.gteeri.moblimiter.pets.PetListener;
import dev.gteeri.moblimiter.pets.PetManager;
import dev.gteeri.moblimiter.util.Msg;
import dev.gteeri.moblimiter.zones.ZoneRegistry;
import dev.gteeri.moblimiter.zones.ZonesGui;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class MobLimiterPlugin extends JavaPlugin {

    private PluginConfig pluginConfig;
    private Msg msg;
    private FreezeManager freezeManager;
    private ClusterScanner clusterScanner;
    private LimitService limitService;
    private PetManager petManager;
    private ZoneRegistry zoneRegistry;
    private ZonesGui zonesGui;
    private StatsGui statsGui;
    private ConfigGui configGui;
    private MainMenuGui mainMenuGui;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.pluginConfig = new PluginConfig(this);
        this.msg = new Msg(this);
        this.freezeManager = new FreezeManager(this);
        this.limitService = new LimitService(this);
        this.petManager = new PetManager(this);
        this.zoneRegistry = new ZoneRegistry(this);
        this.zonesGui = new ZonesGui(this);
        this.statsGui = new StatsGui(this);
        this.configGui = new ConfigGui(this);
        this.mainMenuGui = new MainMenuGui(this);
        this.clusterScanner = new ClusterScanner(this);

        var pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new FreezeListener(this), this);
        pluginManager.registerEvents(new LimitListener(this), this);
        pluginManager.registerEvents(new PetListener(this), this);
        pluginManager.registerEvents(zonesGui, this);
        pluginManager.registerEvents(statsGui, this);
        pluginManager.registerEvents(configGui, this);
        pluginManager.registerEvents(mainMenuGui, this);

        PluginCommand command = getCommand("moblimit");
        if (command != null) {
            MobLimitCommand executor = new MobLimitCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        petManager.start();
        clusterScanner.start();
        getLogger().info("MobLimiter enabled (Folia-ready)");
    }

    @Override
    public void onDisable() {
        if (clusterScanner != null) {
            clusterScanner.stop();
        }
        if (petManager != null) {
            petManager.stop();
        }
        getLogger().info("MobLimiter disabled");
    }

    public void reloadPluginConfig() {
        reloadConfig();
        this.pluginConfig = new PluginConfig(this);
        this.limitService.invalidateCache();
        this.clusterScanner.restart();
    }

    /** Persist a single config value and apply it live (used by the config GUI). */
    public void updateConfigValue(String path, Object value) {
        getConfig().set(path, value);
        saveConfig();
        reloadPluginConfig();
    }

    public PluginConfig cfg() {
        return pluginConfig;
    }

    public Msg msg() {
        return msg;
    }

    public FreezeManager freezer() {
        return freezeManager;
    }

    public LimitService limits() {
        return limitService;
    }

    public PetManager pets() {
        return petManager;
    }

    public ZoneRegistry zones() {
        return zoneRegistry;
    }

    public ZonesGui zonesGui() {
        return zonesGui;
    }

    public StatsGui statsGui() {
        return statsGui;
    }

    public ConfigGui configGui() {
        return configGui;
    }

    public MainMenuGui mainMenu() {
        return mainMenuGui;
    }
}
