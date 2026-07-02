package dev.gteeri.moblimiter;

import dev.gteeri.moblimiter.command.MobLimitCommand;
import dev.gteeri.moblimiter.config.PluginConfig;
import dev.gteeri.moblimiter.freeze.ClusterScanner;
import dev.gteeri.moblimiter.freeze.FreezeListener;
import dev.gteeri.moblimiter.freeze.FreezeManager;
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
        this.clusterScanner = new ClusterScanner(this);

        var pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new FreezeListener(this), this);
        pluginManager.registerEvents(new LimitListener(this), this);
        pluginManager.registerEvents(new PetListener(this), this);
        pluginManager.registerEvents(zonesGui, this);

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
}
