package me.dogdisguise.siegeplugin;

import me.ryanhamshire.GriefPrevention.*;
import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;


import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DataManager {
    private GriefPrevention griefPrevention = (GriefPrevention) Bukkit.getServer().getPluginManager().getPlugin("GriefPrevention");
    public FightData startFightData;
    //in-memory cache for player data
    static protected ConcurrentHashMap<UUID, PlayerFightData> playerNameToPlayerFightDataMap = new ConcurrentHashMap<>();
    static protected ConcurrentHashMap<Long, ClaimFightData> claimIdToClaimDataMap = new ConcurrentHashMap<>();
    //in-memory cache for claim data



    //starts a fight on a claim
    //does NOT check fight cooldowns, see onCooldown() below
    synchronized public void startfight(Player attacker, Player defender, Claim defenderClaim) {
        //fill-in the necessary fightData instance
        this.startFightData = new FightData(attacker, defender, defenderClaim, false, true, false);
        startFightData.PermissionGiver(attacker);
        PlayerFightData attackerData = this.getPlayerFightData(attacker.getUniqueId());
        PlayerFightData defenderData = this.getPlayerFightData(defender.getUniqueId());
        if (defenderClaim == null) return;

        ClaimFightData claimfightData = this.getClaimFightData(defenderClaim.getID());
        defenderClaim.areExplosivesAllowed = true;
        attackerData.fightData = startFightData;
        defenderData.fightData = startFightData;
        claimfightData.fightData = startFightData;

        //start a task to monitor the fight
        //why isn't this a "repeating" task?
        //because depending on the status of the fight at the time the task runs, there may or may not be a reason to run the task again
        FightCheckupTask task = new FightCheckupTask(startFightData);
        startFightData.checkupTaskID = Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(PirateSiege.getPlugin(PirateSiege.class), task, 0);

        //create timers for fight
        FightTimer fightTimer = new FightTimer();
        startFightData.fightBossBar(fightTimer);
        fightTimer.enable(claimfightData.fightData, SiegeConfig.instance.siegeAttackStage, "normalTimer");
    }

    //ends a fight
    //either winnerName or loserName can be null, but not both
    synchronized public void endFight(FightData fightData, String winnerName, String loserName, List<ItemStack> drops) {
        endFight(fightData, winnerName, loserName, drops, SiegeConfig.instance.siegeDoorsOpen);
    }
    public void spawnFirework(Location location) {
        Firework firework = (Firework) location.getWorld().spawnEntity(location, EntityType.FIREWORK);
        FireworkMeta meta = firework.getFireworkMeta();

        FireworkEffect effect = FireworkEffect.builder()
                .with(FireworkEffect.Type.BALL)
                .flicker(true)
                .trail(true)
                .withColor(Color.RED)
                .withFade(Color.YELLOW)
                .build();

        meta.addEffect(effect);
        meta.setPower(0);
        firework.setFireworkMeta(meta);
    }
    synchronized public void endFight(FightData fightData, String winnerName, String loserName, List<ItemStack> drops, int WinTime) {

        PirateSiege.instance.getServer().getScheduler().cancelTask(fightData.checkupTaskID);
        fightData.grantAccess = false;
        fightData.fightStage = false;

        spawnFirework(Bukkit.getPlayer(winnerName).getLocation());

        //determine winner and loser
        if (winnerName == null && loserName != null) {
            if (fightData.attacker.getName().equals(loserName)) {
                winnerName = fightData.defender.getName();
            } else {
                winnerName = fightData.attacker.getName();
            }
        } else if (winnerName != null && loserName == null) {
            if (fightData.attacker.getName().equals(winnerName)) {
                loserName = fightData.defender.getName();
            } else {
                loserName = fightData.attacker.getName();
            }
        }

        //if the attacker won, plan to open the doors for looting
        if (fightData.attacker.getName().equals(winnerName)) {

            //ensure second winner timer does not appear
            fightData.grantAccess = true;
            if (!fightData.winnerStage) {
                FightTimer fightTimer = new FightTimer();
                fightData.fightBossBar(fightTimer);
                fightTimer.enable(fightData, WinTime, "winTimer");
            }
        }


        //start a cooldown for this attacker/defender pair
        Long now = Calendar.getInstance().getTimeInMillis();
        Long cooldownEnd = now + 1000 * 60 * SiegeConfig.instance.siegeCooldown;  //one hour from now
        this.fightCooldownRemaining.put(fightData.attacker.getName() + "_" + fightData.defender.getName(), cooldownEnd);

        for (Claim claim: fightData.claims) {
            claim.areExplosivesAllowed = false;
        }

        //cancel the fight checkup task
        PirateSiege.instance.getServer().getScheduler().cancelTask(fightData.checkupTaskID);

        //notify everyone who won and lost
        if (winnerName != null && loserName != null) {
            GriefPrevention.instance.getServer().broadcastMessage(winnerName + " defeated " + loserName + " in siege warfare!");
        }

        //if the claim should be opened to looting
        if (fightData.grantAccess) {
            //ejects all defenders from claim
            if (SiegeConfig.instance.ejectDefendersDuringLooting) {
                if (!fightData.defender.isDead() && fightData.defender.isOnline()) {
                    griefPrevention.ejectPlayer(fightData.defender);
                }
                if (!fightData.secondaryDefenders.isEmpty()) {
                    for (Player player : fightData.secondaryDefenders) {
                        if (!player.isDead() && player.isOnline()) {
                            griefPrevention.ejectPlayer(player);
                        }
                    }
                }
            }
            Player winner = GriefPrevention.instance.getServer().getPlayer(winnerName);
            if (winner != null) {
                //notify the winner
                this.sendMessage(winner, TextMode.Success, Messages.SiegeWinDoorsOpen);

            }
        } else {
            //ejects all attackers if they loes
            if (SiegeConfig.instance.ejectAttackersAfterSiege) {
                if (!fightData.attacker.isDead() && fightData.attacker.isOnline()) {
                    griefPrevention.ejectPlayer(fightData.attacker);
                }
                if (!fightData.secondaryAttackers.isEmpty()) {
                    for (Player player : fightData.secondaryAttackers) {
                        if (!player.isDead() && player.isOnline()) {
                            griefPrevention.ejectPlayer(player);
                        }
                    }
                }
            }

            fightData.PermissionRemover(null);
            fightData.setToNull(this);
        }

        //if the fight ended due to death, transfer inventory to winner
        if (drops != null) {

            Player winner = GriefPrevention.instance.getServer().getPlayer(winnerName);

            Player loser = GriefPrevention.instance.getServer().getPlayer(loserName);

            if (!loser.getInventory().isEmpty()) {
                loser.getInventory().clear();
            }

            if (winner != null && loser != null) {
                //try to add any drops to the winner's inventory
                for (ItemStack stack : drops) {
                    if (stack == null || stack.getType() == Material.AIR || stack.getAmount() == 0) continue;

                    HashMap<Integer, ItemStack> wontFitItems = winner.getInventory().addItem(stack);

                    //drop any remainder on the ground at his feet
                    Object[] keys = wontFitItems.keySet().toArray();
                    Location winnerLocation = winner.getLocation();
                    for (Map.Entry<Integer, ItemStack> wontFitItem : wontFitItems.entrySet()) {
                        winner.getWorld().dropItemNaturally(winnerLocation, wontFitItem.getValue());
                    }
                }

                drops.clear();
            }
        }
        this.getPlayerFightData(fightData.defender.getUniqueId()).lastFightEndTimeStamp = System.currentTimeMillis();
    }

    //timestamp for each fight cooldown to end
    private final HashMap<String, Long> fightCooldownRemaining = new HashMap<>();

    synchronized public PlayerFightData getPlayerFightData(UUID playerID) {
        //first, look in memory
        PlayerFightData playerfightData = this.playerNameToPlayerFightDataMap.get(playerID);

        //if not there, build a fresh instance with some blanks for what may be in secondary storage
        if (playerfightData == null) {
            playerfightData = new PlayerFightData();
            playerfightData.playerID = playerID;

            //shove that new player data into the hash map cache
            this.playerNameToPlayerFightDataMap.put(playerID, playerfightData);
        }

        return playerfightData;
    }

    synchronized public ClaimFightData getClaimFightData(long claimId) {
        //first, look in memory
        ClaimFightData claimFightData = this.claimIdToClaimDataMap.get(claimId);

        //if not there, build a fresh instance with some blanks for what may be in secondary storage
        if (claimFightData == null) {
            claimFightData = new ClaimFightData();
            claimFightData.claimId = claimId;

            //shove that new player data into the hash map cache
            this.claimIdToClaimDataMap.put(claimId, claimFightData);
        }

        return claimFightData;
    }

    private String[] messages;


    public synchronized Claim getClaim(long id) {



        return griefPrevention.dataStore.getClaim(id);
    }

    synchronized public PlayerData getPlayerData(UUID playerID) {


        return griefPrevention.dataStore.getPlayerData(playerID);
    }

    //sends a color-coded message to a player
    public static void sendMessage(Player player, ChatColor color, String message) {
        if (message == null || message.length() == 0) return;

        if (player == null) {
            GriefPrevention.AddLogEntry(color + message);
        } else {
            player.sendMessage(color + message);
        }
    }


    public static void sendMessage(Player player, ChatColor color, Messages messageID, String... args) {

        GriefPrevention griefPrevention = (GriefPrevention) Bukkit.getServer().getPluginManager().getPlugin("GriefPrevention");
        griefPrevention.sendMessage(player, color, messageID, args);
    }

    public List<ItemStack> getPlayerInventory(Player player) {
        ItemStack[] inventoryContents = player.getInventory().getContents();
        List<ItemStack> items = new ArrayList<>();

        for (ItemStack item : inventoryContents) {
            if (item != null && !item.getType().equals(Material.AIR)) {
                items.add(item);
            }
        }

        return items;
    }
}
