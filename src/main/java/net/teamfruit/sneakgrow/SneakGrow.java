package net.teamfruit.sneakgrow;

import org.bukkit.configuration.Configuration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

public final class SneakGrow extends JavaPlugin {
    public static Logger log;

    public static int cooldown;
    public static boolean enableSaplings;
    public static boolean enableCrops;
    public static boolean showParticles = true;

    @Override
    public void onEnable() {
        // Plugin startup logic
        log = getLogger();

        Configuration config = getConfig();
        cooldown = config.getInt("Tweaks.cooldownTicks", 5);
        enableSaplings = config.getBoolean("Tweaks.growSaplings", true);
        enableCrops = config.getBoolean("Tweaks.growCrops", true);
        showParticles = config.getBoolean("Tweaks.showParticles", true);
        saveDefaultConfig();

        getServer().getPluginManager().registerEvents(new SneakHandler(), this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
