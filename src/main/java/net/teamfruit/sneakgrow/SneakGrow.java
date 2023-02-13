package net.teamfruit.sneakgrow;

import org.bukkit.configuration.Configuration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

public final class SneakGrow extends JavaPlugin {
    public static Logger log;

    public static int cooldown;
    public static boolean enableSaplings;
    public static boolean enableCrops;
    public static boolean showParticles;
    public static int blockRadius;
    public static int mobRadius;
    public static double blockPercentage;
    public static double mobPercentage;

    @Override
    public void onEnable() {
        // Plugin startup logic
        log = getLogger();

        Configuration config = getConfig();
        cooldown = config.getInt("Tweaks.cooldownTicks", 5);
        enableSaplings = config.getBoolean("Tweaks.growSaplings", true);
        enableCrops = config.getBoolean("Tweaks.growCrops", true);
        showParticles = config.getBoolean("Tweaks.showParticles", true);
        blockRadius = config.getInt("Tweaks.blockRadius", 5);
        mobRadius = config.getInt("Tweaks.mobRadius", 5);
        blockPercentage = config.getDouble("Tweaks.blockPercentage", 0.1);
        mobPercentage = config.getDouble("Tweaks.mobPercentage", 0.05);
        saveDefaultConfig();

        getServer().getPluginManager().registerEvents(new SneakHandler(), this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
