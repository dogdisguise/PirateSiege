package me.dogdisguise.siegeplugin;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class FightTimer {
    private BossBar bossBar;
    private int countdown;
    private double time;
    private BukkitTask countdownTask;
    public DataManager dataManager = new DataManager();
    public void enable(FightData fightData, int stringTime, String type) {
        this.time = (double) stringTime;
        this.bossBar = Bukkit.createBossBar(getTitle(stringTime, type), getBarColor(type), getBarStyle(type));
        this.bossBar.setProgress(1.0);
        this.bossBar.setVisible(true);

        // Ensure another winner stage bar does not appear a second time
        if (type.equalsIgnoreCase("winTimer") && !fightData.winnerStage) {
            fightData.winnerStage = true;
            fightData.fightStage = false;
        }

        this.bossBar.addPlayer(fightData.attacker);
        this.bossBar.addPlayer(fightData.defender);

        countdown = stringTime;

        countdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                countdown--;
                double progress = countdown / ((double) time);

                if (countdown > 0.0) {
                    bossBar.setProgress(progress);
                    bossBar.setTitle(getTitle(countdown, type));
                }

                if (((countdown <= 0 || !fightData.fightStage) && type.equalsIgnoreCase("normalTimer")) || fightData == null) {
                    this.cancel(); // Cancel the task
                    if (countdown <= 0) {
                        dataManager.endFight(fightData, fightData.defender.getName(), fightData.attacker.getName(), null);
                    }
                    disable();
                } else if ((countdown <= 0 && type.equalsIgnoreCase("winTimer")) || fightData == null) {
                    this.cancel(); // Cancel the task
                    // Clear fight data from players
                    fightData.grantAccess = false;
                    if (fightData != null) {

                        fightData.PermissionRemover(null);
                        fightData.setToNull(dataManager);
                    }
                    disable();
                }
            }
        }.runTaskTimer(JavaPlugin.getPlugin(PirateSiege.class), 0L, 20L);
    }

    public void disable() {
        if (this.bossBar != null) {
            this.bossBar.removeAll();
            this.bossBar.setVisible(false);
        }
    }

    public void addPlayer(Player player) {
        this.bossBar.addPlayer(player);
    }
    public void removePlayer(Player player) {
        this.bossBar.removePlayer(player);
    }

    private String getTitle(int timeLeft, String type) {
        int minutes = (int) Math.ceil(timeLeft / 60.0);
        String timeType = type.equalsIgnoreCase("normalTimer") ? "minutes" : "minutes for the winners";
        return "There is " + minutes + " " + timeType + " left.";
    }

    private BarColor getBarColor(String type) {
        return type.equalsIgnoreCase("normalTimer") ? BarColor.YELLOW : BarColor.BLUE;
    }

    private BarStyle getBarStyle(String type) {
        return type.equalsIgnoreCase("normalTimer") ? BarStyle.SEGMENTED_10 : BarStyle.SEGMENTED_6;
    }
    public void addTime(int timeAmount) {
        if (timeAmount > 0) {
            this.countdown += timeAmount;
            this.time += (double) timeAmount;
            return;
        }
        this.countdown += timeAmount;
    }
}
