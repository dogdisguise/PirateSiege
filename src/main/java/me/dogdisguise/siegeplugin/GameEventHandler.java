package me.dogdisguise.siegeplugin;

import me.ryanhamshire.GriefPrevention.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;


public class GameEventHandler implements Listener {

    public DataManager dataManager = new DataManager();

    public GriefPrevention griefPrevention = (GriefPrevention) Bukkit.getServer().getPluginManager().getPlugin("GriefPrevention");
    private Claim claimTo;


    @org.bukkit.event.EventHandler
    public void onBlockBreak(BlockBreakEvent breakEvent) {
        Player player = breakEvent.getPlayer();
        Block block = breakEvent.getBlock();
        PlayerFightData playerFightData = dataManager.getPlayerFightData(player.getUniqueId());

        if (playerFightData.fightData != null) {
            if (griefPrevention.dataStore.getPlayerData(breakEvent.getPlayer().getUniqueId()).ignoreClaims) {
                return;
            }
            Claim claim = griefPrevention.dataStore.getClaimAt(block.getLocation(), true, true, null);
            boolean blockAtCorrectClaim = playerFightData.fightData.claims.contains(claim);
            boolean isSiegeBlock = SiegeConfig.instance.siegeBlocks.contains(block.getType());
            boolean isWinnerBlock = SiegeConfig.instance.winnerSiegeBlocks.contains(block.getType());

            if (blockAtCorrectClaim && ((!isSiegeBlock && !playerFightData.fightData.grantAccess) || (!isWinnerBlock && playerFightData.fightData.grantAccess))) {
                dataManager.sendMessage(player, TextMode.Err, Messages.NonSiegeMaterial);
                breakEvent.setCancelled(true);

            }
        }

    }


    @EventHandler(priority = EventPriority.LOWEST)
    public void PlayerDeathGetter(PlayerDeathEvent deathEvent) {
        FightData fightData = dataManager.getPlayerFightData(deathEvent.getEntity().getPlayer().getUniqueId()).fightData;
        if (fightData != null) {
            Player deadPlayer = deathEvent.getEntity().getPlayer();
            if (fightData.grantAccess == true) {
                return;
            }
            if (deadPlayer != fightData.attacker && deadPlayer != fightData.defender) {
                if (fightData.secondaryDefenders.contains(deadPlayer)) {
                    fightData.fightTimer.addTime(SiegeConfig.instance.SecondaryDeathAffectsTimerBy * 60);
                    return;
                } else if (fightData.secondaryAttackers.contains(deadPlayer)) {
                    fightData.fightTimer.addTime(-(SiegeConfig.instance.SecondaryDeathAffectsTimerBy * 60));
                    return;
                }
            }

            if (deadPlayer == fightData.defender) {
                dataManager.endFight(fightData, fightData.attacker.getName(), deadPlayer.getName(), dataManager.getPlayerInventory(deadPlayer));
            } else if (deadPlayer == fightData.attacker) {
                dataManager.endFight(fightData, fightData.defender.getName(), deadPlayer.getName(), dataManager.getPlayerInventory(deadPlayer));
            }
            deathEvent.getDrops().clear();
        }

    }


    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void PlayerLeft(PlayerQuitEvent quitEvent) {
        if (dataManager.getPlayerFightData(quitEvent.getPlayer().getUniqueId()).fightData != null) {
            Player gonePlayer = quitEvent.getPlayer();
            FightData fightData = dataManager.getPlayerFightData(gonePlayer.getUniqueId()).fightData;
            if (fightData.grantAccess == true) {
                return;
            }
            //remove secondary attackers and defenders from siege if they leave
            if (fightData.secondaryDefenders.contains(gonePlayer)) {
                fightData.secondaryDefenders.remove(gonePlayer);
                return;
            } else if (fightData.secondaryAttackers.contains(gonePlayer)) {
                fightData.secondaryAttackers.remove(gonePlayer);
                return;
            }
            //end siege if the player who left was a primary defender or attacker
            if (gonePlayer == fightData.defender) {
                dataManager.endFight(fightData, fightData.attacker.getName(), gonePlayer.getName(), dataManager.getPlayerInventory(gonePlayer));
            } else if (gonePlayer == fightData.attacker) {
                dataManager.endFight(fightData, fightData.defender.getName(), gonePlayer.getName(), dataManager.getPlayerInventory(gonePlayer));

            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockExplode(BlockExplodeEvent event) {
        List<Block> explodedBlocks = event.blockList();
        Location explosionLocation = event.getBlock().getLocation();

        // Check claim at original explosion location and nearby points
        if (hasClaimAtLocation(explosionLocation) ||
                hasClaimAtLocation(getOffsetLocation(explosionLocation, event.getYield(), 0, 0)) ||
                hasClaimAtLocation(getOffsetLocation(explosionLocation, -event.getYield(), 0, 0)) ||
                hasClaimAtLocation(getOffsetLocation(explosionLocation, 0, 0, event.getYield())) ||
                hasClaimAtLocation(getOffsetLocation(explosionLocation, 0, 0, -event.getYield()))) {
            event.getBlock().getWorld().createExplosion(event.getBlock().getLocation(), event.getYield(), false, false);

            event.setCancelled(true);

            for (Block block : explodedBlocks) {
                if (SiegeConfig.instance.siegeBlocks.contains(block.getType())) {
                    block.setType(Material.AIR);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityExplode(EntityExplodeEvent event) {
        List<Block> explodedBlocks = event.blockList();
        Location explosionLocation = event.getLocation();
        int Yeil = 10;

        // Check claim at original explosion location and nearby points
        if (hasClaimAtLocation(explosionLocation) ||
                hasClaimAtLocation(getOffsetLocation(explosionLocation, Yeil, 0, 0)) ||
                hasClaimAtLocation(getOffsetLocation(explosionLocation, -Yeil, 0, 0)) ||
                hasClaimAtLocation(getOffsetLocation(explosionLocation, 0, 0, Yeil)) ||
                hasClaimAtLocation(getOffsetLocation(explosionLocation, 0, 0, -Yeil))) {

            event.setCancelled(true);

            for (Block block : explodedBlocks) {
                if (SiegeConfig.instance.siegeBlocks.contains(block.getType())) {
                    event.getEntity().getWorld().createExplosion(block.getLocation(), 6F, false, false);
                    block.setType(Material.AIR);
                }
            }
        }
    }

    private boolean hasClaimAtLocation(Location location) {

        Claim claim = griefPrevention.dataStore.getClaimAt(location, true, true, null);
        return claim != null;
    }

    private Location getOffsetLocation(Location location, double xOffset, double yOffset, double zOffset) {
        return new Location(location.getWorld(),
                location.getX() + xOffset,
                location.getY() + yOffset,
                location.getZ() + zOffset);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String[] commandParts = event.getMessage().split(" ");
        String commandName = commandParts[0].toLowerCase(); // Get the command name
        List<String> args = new ArrayList<>();

        for (String part : commandParts) {
            if (part != commandName) {
                args.add(part);
            }
        }

        //forbiden command list
        List<String> targetCommands = SiegeConfig.instance.forbiddenSiegeCommands;
        List<String> targetCommandsExtra = new ArrayList<>(Arrays.asList(
                "/abandonclaim", "/unclaim", "/declaim", "/removeclaim", "/disclaim",
                "/abandontoplevelclaim",
                "/abandonallclaims",
                "/trust", "/tr",
                "/untrust", "/ut",
                "/containertrust", "/ct",
                "/accesstrust", "/at",
                "/permissiontrust", "/pt",
                "/subdivideclaims", "/sc", "/subdivideclaim",
                "/restrictsubclaim", "/rsc",
                "/adjustbonusclaimblocks", "/acb",
                "/adjustbonusclaimblocksall", "/acball",
                "/setaccruedclaimblocks", "/scb",
                "/deleteclaim",
                "/deleteallclaims",
                "/deleteclaimsinworld", "/deleteallclaimsinworld", "/clearclaimsinworld", "/clearallclaimsinworld",
                "/deleteuserclaimsinworld", "/deletealluserclaimsinworld", "/clearuserclaimsinworld", "/clearalluserclaimsinworld",
                "/adminclaims", "/ac",
                "/restorenature", "/rn",
                "/restorenatureaggressive", "/rna",
                "/restorenaturefill", "/rnf",
                "/basicclaims", "/bc",
                "/extendclaim",
                "/claim",
                "/buyclaimblocks", "/buyclaim",
                "/sellclaimblocks", "/sellclaim",
                "/trapped",
                "/trustlist",
                "/claimbook",
                "/claimexplosions", "/claimexplosion", "/giveclaim"
        ));
        if (commandName.equalsIgnoreCase("/siege")) {
            new CommandHandler().SiegeCommand(event.getPlayer(), args);
            event.setCancelled(true);
        }

        if (commandName.equalsIgnoreCase("/teleport")) {
            PlayerFightData playerFightData = dataManager.getPlayerFightData(Bukkit.getPlayer(args.get(0)).getUniqueId());
            playerFightData.bypassSiegeTeleport = true;
            BukkitTask task = new BukkitRunnable() {
                @Override
                public void run() {
                    playerFightData.bypassSiegeTeleport = false;
                }
            }.runTaskLater(PirateSiege.getPlugin(PirateSiege.class), 20L);
        }


        if ((targetCommands.contains(commandName) || targetCommandsExtra.contains(commandName)) && !griefPrevention.dataStore.getPlayerData(event.getPlayer().getUniqueId()).ignoreClaims) {
            if (dataManager.getPlayerFightData(event.getPlayer().getUniqueId()).fightData == null) {
                return;
            }

            event.setCancelled(true); // Cancel the command
            DataManager.sendMessage(event.getPlayer(), TextMode.Err, "You can not use this command during a siege");// Optional message
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerTeleport(PlayerTeleportEvent event) {


        Player player = event.getPlayer();
        PlayerFightData playerFightData = dataManager.getPlayerFightData(player.getUniqueId());

        if (playerFightData.fightData != null) {

            if (griefPrevention.dataStore.getPlayerData(event.getPlayer().getUniqueId()).ignoreClaims) {
                return;
            }
            Claim claimTo = griefPrevention.dataStore.getClaimAt(event.getTo(), false, null);
            Claim claimFrom = griefPrevention.dataStore.getClaimAt(event.getFrom(), false, null);
            PlayerTeleportEvent.TeleportCause cause = event.getCause();

            if ((cause == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT || cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL)) {
                if (claimTo != null && !playerFightData.fightData.grantAccess) {
                    GriefPrevention.sendMessage(player, TextMode.Err, "You can't teleport onto a claim during a siege!");
                    event.setCancelled(true);
                    if (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
                        player.getInventory().addItem(new ItemStack(Material.ENDER_PEARL));
                    }
                }
                return;
            }
            if ((dataManager.getClaimFightData(claimTo.getID()).fightData == null && dataManager.getClaimFightData(claimTo.getID()).fightData == null) || (playerFightData.bypassSiegeTeleport == true)) {
                return;
            }

            GriefPrevention.sendMessage(player, TextMode.Err, Messages.BesiegedNoTeleport);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block clickedBlock = event.getClickedBlock();

        if (clickedBlock == null) {
            return;
        }

        FightData playerFightData = dataManager.getPlayerFightData(player.getUniqueId()).fightData;

        if (playerFightData != null) {
            if (griefPrevention.dataStore.getPlayerData(player.getUniqueId()).ignoreClaims) {
                return;
            }

            List<String> materialContains = new ArrayList<>(Arrays.asList("door", "gate", "button", "sign", "lever"));
            String interactEventItem = clickedBlock.getType().toString().toLowerCase();

            if (playerFightData.grantAccess && materialContains.stream().anyMatch(interactEventItem::contains)
                    || (clickedBlock.getState() instanceof Container)) {
                return;
            }

            if (event.getAction() != Action.LEFT_CLICK_BLOCK) {
                Claim claim = griefPrevention.dataStore.getClaimAt(clickedBlock.getLocation(), false, true, null);
                if (claim != null && playerFightData.claims.contains(claim)) {
                    dataManager.sendMessage(player, TextMode.Err, "You can't do that!");
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void PlayerPvp(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player && event.getDamager() instanceof Player) {
            Player defender = (Player) event.getEntity();
            Player attacker = (Player) event.getDamager();
            FightData fightData = dataManager.getPlayerFightData(defender.getUniqueId()).fightData;

            if (fightData == null) {
                return;
            }

            Set<Player> attackers = fightData.secondaryAttackers;
            attackers.add(fightData.attacker);
            Set<Player> defenders = fightData.secondaryDefenders;
            defenders.add(fightData.defender);

            if ((defenders.contains(defender) && defenders.contains(attacker)) || (attackers.contains(defender) && attackers.contains(attacker))) {
                event.setCancelled(true);
                DataManager.sendMessage(attacker, TextMode.Info, "You can't attack your teammate!");
            }

        }
    }
}