/*
    GriefPrevention Server Plugin for Minecraft
    Copyright (C) 2012 Ryan Hamshire

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.dogdisguise.siegeplugin;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

//information about an ongoing fight
public class FightData {
    public Player temporaryAttacker = null;
    public Player temporaryDefender = null;

    public Player defender;
    public Player attacker;
    public Set<Player> secondaryAttackers;
    public Set<Player> secondaryDefenders;
    public ArrayList<Claim> claims;
    public int checkupTaskID;
    public boolean grantAccess;
    public boolean fightStage;

    public boolean winnerStage;

    public FightTimer fightTimer;

    public Set<Player> PlayersTotal = new HashSet<>();

    public FightData(Player attacker, Player defender, Claim claim, Boolean grantAccess, Boolean fightStage, Boolean winnerStage) {
        this.winnerStage = winnerStage;
        this.defender = defender;
        this.attacker = attacker;
        this.claims = new ArrayList<>();
        this.claims.add(claim);
        this.grantAccess = grantAccess;
        this.fightStage = fightStage;

        this.secondaryAttackers = new HashSet<>();
        this.secondaryDefenders = new HashSet<>();
    }

    public void TotalPlayersRefresh() {
        this.PlayersTotal.clear();
        this.PlayersTotal.addAll(secondaryAttackers);
        this.PlayersTotal.addAll(secondaryDefenders);
    }

    public void AdditionalFightData(Player secondaryAttacker, Player secondaryDefender) {
        TotalPlayersRefresh();

        if (secondaryAttacker != null && this.fightTimer != null) {
            DataManager.sendMessage(secondaryAttacker, TextMode.Info, "You have been added to the fight as an attacker!");
            this.secondaryAttackers.add(secondaryAttacker);
            this.fightTimer.addPlayer(secondaryAttacker);
        }
        if (secondaryDefender != null && this.fightTimer != null) {
            DataManager.sendMessage(secondaryDefender, TextMode.Info, "You have been added to the fight as a defender!");
            this.secondaryDefenders.add(secondaryDefender);
            this.fightTimer.addPlayer(secondaryDefender);
        }

    }


    public void fightBossBar(FightTimer fightTimer) {
        this.fightTimer = fightTimer;
    }

    private final Set<List<Object>> TempPermissionSet = new HashSet<>();

    public void RemovePlayerFromBossBar(Player player) {
        if (player != null && this.fightTimer != null) {
            DataManager.sendMessage(player, TextMode.Info, "You have been removed from a siege.");
            this.fightTimer.removePlayer(player);
        }
    }

    public void setToNull(DataManager dataManager) {
        TotalPlayersRefresh();
        PlayersTotal.stream().forEach(player -> dataManager.getPlayerFightData(player.getUniqueId()).fightData = null);
        claims.stream().forEach(claim -> dataManager.getClaimFightData(claim.getID()).fightData = null);
        FightData fightData = this;
        fightData = null;
    }

    public void PermissionGiver(Player player) {
        for (Claim claim : claims) {
            String id = String.valueOf(player.getUniqueId());
            if (claim.getPermission(id) == ClaimPermission.Build) {
                continue;
            }
            List<Object> innerList = new ArrayList<>(Arrays.asList(id, claim, claim.getPermission(id)));
            this.TempPermissionSet.add(innerList);
            claim.setPermission(id, ClaimPermission.Build);
        }
    }

    public void PermissionRemover(Player player) {

        for (List<Object> innerList : TempPermissionSet) {
            String id = (String) innerList.get(0);
            Claim claim = (Claim) innerList.get(1);
            ClaimPermission permissionValue = (ClaimPermission) innerList.get(2);
            if (player == null) {
                if (permissionValue != null) {
                    claim.setPermission(id, permissionValue);
                } else {
                    claim.dropPermission(id);
                }
            } else if (player != null && id.equals(String.valueOf(player.getUniqueId()))) {
                if (permissionValue != null) {
                    claim.setPermission(id, permissionValue);
                } else {
                    claim.dropPermission(id);
                }
            }
        }
    }
}
