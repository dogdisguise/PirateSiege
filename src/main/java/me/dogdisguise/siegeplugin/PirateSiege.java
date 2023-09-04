package me.dogdisguise.siegeplugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PirateSiege extends JavaPlugin implements Listener {
    public static PirateSiege instance;
    private File configFile;
    private FileConfiguration configuration;


    @Override
    public void onEnable() {
        instance = this;
        Bukkit.getPluginManager().registerEvents(new GameEventHandler(), this);
        Bukkit.getPluginManager().registerEvents(this,this);
        getCommand("surrender").setExecutor(new CommandHandler());
        getCommand("siegedata").setExecutor(new CommandHandler());
        getCommand("siegeaccept").setExecutor(new CommandHandler());
        getCommand("switchteam").setExecutor(new CommandHandler());
        getCommand("siegedeny").setExecutor(new CommandHandler());

        getLogger().info("The plugin 'PirateSiege' has been enabled.");
        loadConfig();
    }

    @Override
    public void onDisable() {
        saveConfig();
        getLogger().info("The plugin 'PirateSiege' has been disabled. Removing all siege permissions from claims");
    }


    public void loadConfig() {
        configFile = new File(getDataFolder(), "config.yml");
        configuration = YamlConfiguration.loadConfiguration(configFile);

        // Load default values from resources and apply to configuration
        InputStream defaultConfigStream = getResource("config.yml");
        if (defaultConfigStream != null) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            FileConfiguration defconf = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultConfigStream));
            config.addDefaults(defconf);
            config.setDefaults(defconf);
            this.saveDefaultConfig();
        }
        SiegeConfig siegeConfig = new SiegeConfig(configuration);
        SiegeConfig.instance = siegeConfig;

    }


    public static Set<Material> convertStringListToMaterialSet(List<String> materialNames) {
        Set<Material> materialSet = new HashSet<>();

        for (String materialName : materialNames) {
            Material material = Material.matchMaterial(materialName);
            if (material != null) {
                materialSet.add(material);
            }
        }

        return materialSet;
    }
}
