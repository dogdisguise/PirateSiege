
package me.dogdisguise.siegeplugin;

import me.ryanhamshire.GriefPrevention.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;


import java.util.Collection;
import java.util.Set;
import java.util.function.Supplier;

class FightCheckupTask implements Runnable {
    private final FightData fightData;
    public DataManager dataManager = new DataManager();

    public FightCheckupTask(FightData fightData) {
        this.fightData = fightData;
    }

    public GriefPrevention griefPrevention = (GriefPrevention) Bukkit.getServer().getPluginManager().getPlugin("GriefPrevention");


    @Override
    public void run() {
        Collection<? extends Player> onlinePlayersCollection = Bukkit.getOnlinePlayers();


        for (Player onlinePlayer : onlinePlayersCollection) {
            if (playerRemains(onlinePlayer)) {
                PlayerFightData onlinePlayerData = dataManager.getPlayerFightData(onlinePlayer.getUniqueId());
                if (onlinePlayerData.fightData == null && !dataManager.getPlayerData(onlinePlayer.getUniqueId()).ignoreClaims) {
                    boolean hasPermissionOrIsOwner = false;

                    for (Claim claim : this.fightData.claims) {
                        String playerId = String.valueOf(onlinePlayer.getUniqueId());
                        if (claim.getPermission(playerId) != null || claim.ownerID.equals(onlinePlayer.getUniqueId())) {
                            hasPermissionOrIsOwner = true;
                            break;
                        }
                    }

                    if (hasPermissionOrIsOwner) {
                        this.fightData.AdditionalFightData(null, onlinePlayer);
                    } else {
                        this.fightData.AdditionalFightData(onlinePlayer, null);
                    }
                    this.fightData.PermissionGiver(onlinePlayer);
                    onlinePlayerData.fightData = this.fightData;
                    dataManager.getPlayerData(onlinePlayer.getUniqueId()).pvpImmune = false;
                }
            }
        }

        Player defender = this.fightData.defender;
        Player attacker = this.fightData.attacker;

        //determine who's close enough to the fight area to be considered "still here"
        boolean attackerRemains = this.playerRemains(attacker);
        boolean defenderRemains = this.playerRemains(defender);
        if (attackerRemains && defenderRemains) {
            this.scheduleAnotherCheck();
        }
        //otherwise attacker wins if the defender runs away
        else if (attackerRemains && !defenderRemains) {
            dataManager.endFight(this.fightData, attacker.getName(), defender.getName(), null);
        }

        //or defender wins if the attacker leaves
        else if (!attackerRemains && defenderRemains) {
            dataManager.endFight(this.fightData, defender.getName(), attacker.getName(), null);
        }

        //if they both left, but are still close together, the battle continues (check again later)


        //otherwise they both left and aren't close to each other, so call the attacker the winner (defender escaped, possibly after a chase)
        else {
            dataManager.endFight(this.fightData, attacker.getName(), defender.getName(), null);
        }

        if (SiegeConfig.instance.siegeExtends) {
            boolean primaryDefenderClaims = SiegeConfig.instance.primaryDefenderClaimsWithPermsAdded;

            if (SiegeConfig.instance.attackersTriggerExtendSiege) {
                extendSiegeForPlayers(fightData.secondaryAttackers, attacker, primaryDefenderClaims);
            }

            if (SiegeConfig.instance.secondaryDefendersTriggerExtendSiege) {
                extendSiegeForPlayers(fightData.secondaryDefenders, defender, primaryDefenderClaims);
            }
        }
        for (Player player : fightData.secondaryDefenders) {
            if (!playerRemains(player)) {
                fightData.secondaryDefenders.remove(player);
                fightData.RemovePlayerFromBossBar(player);
                fightData.PermissionRemover(player);
                dataManager.getPlayerFightData(player.getUniqueId()).fightData = null;
            }
        }
        for (Player player : fightData.secondaryAttackers) {
            if (!playerRemains(player)) {
                fightData.secondaryAttackers.remove(player);
                fightData.RemovePlayerFromBossBar(player);
                fightData.PermissionRemover(player);
                dataManager.getPlayerFightData(player.getUniqueId()).fightData = null;
            }
        }

    }

    private void extendSiegeForPlayers(Set<Player> players, Player primary, boolean primaryDefenderClaims) {

        players.add(primary);
        for (Player extendingPlayer : players) {
            Claim claim = griefPrevention.dataStore.getClaimAt(extendingPlayer.getLocation(), false, true, null);
            if (claim == null) {return;}
            if (!fightData.claims.contains(claim)) {
                boolean hasDefenderPermission = claim.getPermission(String.valueOf(fightData.defender.getUniqueId())) != null;

                if ((hasDefenderPermission && primaryDefenderClaims) || claim.ownerID.equals(fightData.defender.getUniqueId())) {
                    fightData.claims.add(claim);
                    dataManager.getClaimFightData(claim.getID()).fightData = fightData;
                    fightData.TotalPlayersRefresh();
                    fightData.PlayersTotal.forEach(fightData::PermissionGiver);
                    GriefPrevention.sendMessage(extendingPlayer, TextMode.Info, "You have added a claim to the siege");

                    int addedTime = (claim.getArea() / 1000) * SiegeConfig.instance.MinutesAddedPer1000ClaimBlocksAdded;
                    if (SiegeConfig.instance.ClaimsAddedExtraTime) {
                        fightData.fightTimer.addTime(addedTime);
                    }
                }
            }
        }
    }

    //a player has to be within 25 blocks of the edge of a befightd claim to be considered still in the fight
    private boolean playerRemains(Player player) {
        for (int i = 0; i < this.fightData.claims.size(); i++) {
            Claim claim = this.fightData.claims.get(i);
            if (claim.isNear(player.getLocation(), 50)) {
                return true;
            }
        }

        return false;
    }

    //schedules another checkup later
    private void scheduleAnotherCheck() {
        this.fightData.checkupTaskID = Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(PirateSiege.getPlugin(PirateSiege.class), this, 200L);
    }
}
