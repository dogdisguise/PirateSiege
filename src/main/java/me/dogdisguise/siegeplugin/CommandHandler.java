package me.dogdisguise.siegeplugin;

import me.ryanhamshire.GriefPrevention.*;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Calendar;
import java.util.List;
import java.util.stream.Collectors;

import static net.md_5.bungee.api.chat.ClickEvent.*;


public class CommandHandler implements CommandExecutor {


    private GriefPrevention griefPrevention = (GriefPrevention) Bukkit.getServer().getPluginManager().getPlugin("GriefPrevention");
    public DataManager dataManager = new DataManager();

    public boolean SiegeCommand(Player player, List<String> args) {


        ////error message for when fight mode is disabled
        if (!SiegeConfig.instance.siegeWorlds.contains(player.getWorld().

                getName())) {
            DataManager.sendMessage(player, TextMode.Err, Messages.NonSiegeWorld);
            return true;
        }

        //requires one argument
        if (args.size() > 1) {
            return false;
        }

        //can't start a fight when you're already involved in one
        Player attacker = player;
        PlayerFightData attackerFightData = this.dataManager.getPlayerFightData(attacker.getUniqueId());
        PlayerData attackerData = this.dataManager.getPlayerData(attacker.getUniqueId());


        if (attackerFightData.fightData != null) {
            DataManager.sendMessage(player, TextMode.Err, Messages.AlreadySieging);
            return true;
        }

        //can't start a fight when you're protected from pvp combat
        if (attackerData.pvpImmune) {
            DataManager.sendMessage(player, TextMode.Err, Messages.CantFightWhileImmune);
            return true;
        }


        //if a player name was specified, use that
        Player defender = null;
        if (args.size() >= 1) {
            defender = Bukkit.getServer().getPlayer(String.valueOf(args.get(0)));
            if (defender == null) {
                DataManager.sendMessage(player, TextMode.Err, Messages.PlayerNotFound2);
                return true;
            }
        }


        //otherwise use the last player this player was in pvp combat with
        else if (attackerData.lastPvpPlayer.length() > 0) {
            defender = Bukkit.getServer().getPlayer(attackerData.lastPvpPlayer);
            if (defender == null) {
                return false;
            }
        } else {
            return false;
        }

        // First off, you cannot fight yourself, that's just
        // silly:
        if (attacker.getName().

                equals(defender.getName())) {
            DataManager.sendMessage(player, TextMode.Err, Messages.NoSiegeYourself);
            return true;
        }

        //victim must not have the permission which makes him immune to fight
        if (defender.hasPermission("griefprevention.fightimmune")) {
            DataManager.sendMessage(player, TextMode.Err, Messages.SiegeImmune);
            return true;
        }

        //victim must not be under fight already
        PlayerFightData defenderFightData = this.dataManager.getPlayerFightData(defender.getUniqueId());
        PlayerData defenderData = this.dataManager.getPlayerData(defender.getUniqueId());

        //can't start a fight with a dead player
        if (defender.isDead()) {
            DataManager.sendMessage(player, TextMode.Err, "The player you are trying to siege is dead!");
            return true;
        }


        if (defenderFightData.fightData != null) {
            DataManager.sendMessage(player, TextMode.Err, Messages.AlreadyUnderSiegePlayer);
            return true;
        }

        //victim must not be pvp immune
        if (defenderData.pvpImmune) {
            DataManager.sendMessage(player, TextMode.Err, Messages.NoSiegeDefenseless);
            return true;
        }


        Claim defenderClaim = griefPrevention.dataStore.getClaimAt(defender.getLocation(), true, null);
        if (defenderClaim == null) {
            DataManager.sendMessage(player, TextMode.Err, "That player isn't inside a claim!");
            return true;
        }

        ClaimFightData claimFightData = this.dataManager.getClaimFightData(defenderClaim.getID());


        //defender must have some level of permission there to be protected
        if (Bukkit.getPlayer(defenderClaim.ownerID) != defender) {
            DataManager.sendMessage(player, TextMode.Err, "You can't siege them there!");
            return true;
        }

        //attacker must be close to the claim he wants to fight
        if (!defenderClaim.isNear(attacker.getLocation(), 25)) {
            DataManager.sendMessage(player, TextMode.Err, Messages.SiegeTooFarAway);
            return true;
        }

        //claim can't be under fight already
        if (claimFightData.fightData != null) {
            DataManager.sendMessage(player, TextMode.Err, Messages.AlreadyUnderSiegeArea);
            return true;
        }

        //can't Siege admin claims
        if (defenderClaim.isAdminClaim()) {
            DataManager.sendMessage(player, TextMode.Err, Messages.NoSiegeAdminClaim);
            return true;
        }

        //can't be on cooldown

        Long now = Calendar.getInstance().getTimeInMillis();
        Long cooldownEnd = defenderFightData.lastFightEndTimeStamp + (1000 * 60 * SiegeConfig.instance.siegeCooldown);

        if (now < cooldownEnd) {
            DataManager.sendMessage(player, TextMode.Err, Messages.SiegeOnCooldown);
            return true;
        }


        //start the Siege
        dataManager.startfight(attacker, defender, defenderClaim);

        //confirmation message for attacker, warning message for defender
        DataManager.sendMessage(defender, TextMode.Warn, Messages.SiegeAlert, attacker.getName());
        DataManager.sendMessage(player, TextMode.Success, Messages.SiegeConfirmed, defender.getName());

        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        Player player = (Player) sender;


        if (cmd.getName().equalsIgnoreCase("surrender") && player != null) {
            PlayerFightData defenderFightData = this.dataManager.getPlayerFightData(player.getUniqueId());
            if (defenderFightData.fightData == null) {
                return false;
            }

            FightData fightData = defenderFightData.fightData;
            if (player.equals(fightData.defender)) {
                this.dataManager.endFight(fightData, fightData.attacker.getName(), player.getName(), dataManager.getPlayerInventory(player), SiegeConfig.instance.siegeDoorsOpen + SiegeConfig.instance.surrenderExtendsTimeBy);

                DataManager.sendMessage(player, TextMode.Info, "You have surrendered to " + fightData.attacker.getName());
                DataManager.sendMessage(fightData.attacker, TextMode.Info, player.getName() + " has surrendered to you!");

                return true;
            }
        } else if (cmd.getName().equalsIgnoreCase("siegedata") && player != null) {
            Claim claim = griefPrevention.dataStore.getClaimAt(player.getLocation(), true, null);
            if (claim == null) {
                DataManager.sendMessage(player, TextMode.Warn, "There is no claim here");
                return true;
            }
            FightData fightData = dataManager.getClaimFightData(claim.getID()).fightData;
            if (player.hasPermission("PirateSiege.BesiegeDataCommand") && fightData != null) {
                player.sendMessage(TextMode.Info + "----------------Primary Information---------");
                player.sendMessage("Main Defender: " + fightData.defender.getName());
                player.sendMessage("Main Attacker: " + fightData.attacker.getName());
                player.sendMessage("Claims: ");
                fightData.claims.forEach(claim1 -> player.sendMessage("[ Owner: " + claim1.getOwnerName() + ", " + claim1.getArea() + " claim blocks]"));
                player.sendMessage(TextMode.Info + "----------------Siege Stage-----------------");
                player.sendMessage("Looting Stage: " + fightData.winnerStage);
                player.sendMessage("Granted Access: " + fightData.grantAccess);
                player.sendMessage("Fighting Stage: " + fightData.fightStage);
                player.sendMessage(TextMode.Info + "----------------Secondary Information--------");
                String defenderNames = fightData.secondaryDefenders.stream().map(Player::getName).collect(Collectors.joining(", "));
                String attackerNames = fightData.secondaryAttackers.stream().map(Player::getName).collect(Collectors.joining(", "));
                player.sendMessage("Attackers: " + attackerNames);
                player.sendMessage("Defenders: " + defenderNames);
                return true;

            } else if (!player.hasPermission("PirateSiege.BesiegeDataCommand")) {
                DataManager.sendMessage(player, TextMode.Err, "You don't have permission to run this command!");
                return true;

            } else if (fightData == null) {
                DataManager.sendMessage(player, TextMode.Warn, "There is no siege here");
                return true;
            }
        } else if (cmd.getName().equalsIgnoreCase("switchteam") && player != null) {
            PlayerFightData playerFightData = dataManager.getPlayerFightData(player.getUniqueId());
            FightData fightData = playerFightData.fightData;

            if (fightData == null || playerFightData == null) {
                return true;
            }
            if (fightData.temporaryAttacker != null || fightData.temporaryDefender != null) {
                DataManager.sendMessage(player, TextMode.Err, "ERROR! Please try again in a few seconds...");
            }

            // Create the green check mark message
            BaseComponent[] acceptMessage = new ComponentBuilder("Click ")
                    .color(ChatColor.YELLOW)
                    .append("[✓]")
                    .color(net.md_5.bungee.api.ChatColor.GREEN)
                    .event(new ClickEvent(Action.RUN_COMMAND, "/siegeaccept"))
                    .append(" to welcome them aboard.")
                    .color(ChatColor.YELLOW)
                    .create();

            // Create the red x mark message
            BaseComponent[] denyMessage = new ComponentBuilder("Click ")
                    .color(ChatColor.YELLOW)
                    .append("[X]")
                    .color(net.md_5.bungee.api.ChatColor.RED)
                    .event(new ClickEvent(Action.RUN_COMMAND, "/siegedeny"))
                    .append(" to kindly decline.")
                    .color(ChatColor.YELLOW)
                    .create();

            if (fightData.secondaryDefenders.contains(player) && player != fightData.defender) {
                DataManager.sendMessage(fightData.attacker, TextMode.Instr, player.getName() + " has sent a team switch request! ");
                fightData.attacker.spigot().sendMessage(acceptMessage);
                DataManager.sendMessage(fightData.attacker, TextMode.Instr, " to welcome them aboard, or use ");
                fightData.attacker.spigot().sendMessage(denyMessage);
                DataManager.sendMessage(fightData.attacker, TextMode.Instr, " to kindly decline.");
                fightData.temporaryAttacker = player;
            } else if (fightData.secondaryAttackers.contains(player) && player != fightData.attacker) {
                DataManager.sendMessage(fightData.defender, TextMode.Instr, player.getName() + " has sent a team switch request! ");
                fightData.defender.spigot().sendMessage(acceptMessage);
                DataManager.sendMessage(fightData.defender, TextMode.Instr, " to welcome them aboard, or use ");
                fightData.defender.spigot().sendMessage(denyMessage);
                DataManager.sendMessage(fightData.defender, TextMode.Instr, " to kindly decline.");
                fightData.temporaryDefender = player;
            }
        } else if (cmd.getName().equalsIgnoreCase("siegeaccept") && player != null) {
            PlayerFightData playerFightData = dataManager.getPlayerFightData(player.getUniqueId());
            FightData fightData = playerFightData.fightData;

            if (fightData == null || playerFightData == null) {
                return true;
            }

            if (fightData.temporaryAttacker != null) {
                DataManager.sendMessage(fightData.temporaryAttacker, TextMode.Success, "Your request has been accepted!");
                fightData.secondaryDefenders.remove(fightData.temporaryAttacker);
                fightData.secondaryAttackers.add(fightData.temporaryAttacker);
                DataManager.sendMessage(player,TextMode.Success,"Success! "+ fightData.temporaryAttacker.getName()+" now on your team!");

                fightData.temporaryAttacker = null;
            } else if (fightData.temporaryDefender != null) {
                DataManager.sendMessage(fightData.temporaryDefender, TextMode.Success, "Your request has been accepted!");
                fightData.secondaryAttackers.remove(fightData.temporaryDefender);
                fightData.secondaryDefenders.add(fightData.temporaryDefender);
                DataManager.sendMessage(player,TextMode.Success,"Success! "+ fightData.temporaryDefender.getName()+" now on your team!");
                fightData.temporaryDefender = null;
            }

            return true;
        } else if (cmd.getName().equalsIgnoreCase("siegedeny") && player != null) {
            PlayerFightData playerFightData = dataManager.getPlayerFightData(player.getUniqueId());
            FightData fightData = playerFightData.fightData;

            if (fightData == null || playerFightData == null) {
                return true;
            }


            if (fightData.temporaryAttacker != null) {
                DataManager.sendMessage(fightData.temporaryAttacker, TextMode.Err, "Your request has been denied!");
                DataManager.sendMessage(player,TextMode.Success,fightData.temporaryAttacker.getName() + " has been denied!");
                fightData.temporaryAttacker = null;
            } else if (fightData.temporaryDefender != null) {
                DataManager.sendMessage(fightData.temporaryDefender, TextMode.Err, "Your request has been denied!");
                DataManager.sendMessage(player,TextMode.Success,fightData.temporaryDefender.getName() + " has been denied!");
                fightData.temporaryDefender = null;
            }
            return true;
        }
        return false;
    }

}
