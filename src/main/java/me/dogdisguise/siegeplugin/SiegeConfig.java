package me.dogdisguise.siegeplugin;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Set;

public class SiegeConfig {
    public static SiegeConfig instance;
    public List<String> siegeWorlds;
    public Set<Material> siegeBlocks;
    public Set<Material> winnerSiegeBlocks;
    public int siegeCooldown;
    public int siegeDoorsOpen;
    public int siegeAttackStage;
    public int surrenderExtendsTimeBy;
    public boolean ejectAttackersAfterSiege;
    public boolean ejectDefendersDuringLooting;
    public List<String> forbiddenSiegeCommands;
    public boolean siegeExtends;
    public boolean secondaryDefendersTriggerExtendSiege;
    public boolean attackersTriggerExtendSiege;
    public boolean primaryDefenderClaimsWithPermsAdded;
    public boolean ClaimsAddedExtraTime;
    public int SecondaryDeathAffectsTimerBy;
    public int MinutesAddedPer1000ClaimBlocksAdded;
    public SiegeConfig(FileConfiguration config) {
        siegeWorlds = config.getStringList("siege.worlds");
        siegeBlocks = PirateSiege.convertStringListToMaterialSet(config.getStringList("siege.SiegeBlocks"));
        winnerSiegeBlocks = PirateSiege.convertStringListToMaterialSet(config.getStringList("siege.SiegeWinnerBlocks"));
        siegeCooldown = config.getInt("siege.Times.SiegeCoolDown");
        surrenderExtendsTimeBy = config.getInt("siege.Times.SurrenderingExtendsLootingStageBy");
        siegeDoorsOpen = config.getInt("siege.Times.WinnerStageLength");
        siegeAttackStage = config.getInt("siege.Times.AttackStageLength");
        ejectAttackersAfterSiege = config.getBoolean("siege.EjectionSettings.EjectAttackersAfterSiege");
        ejectDefendersDuringLooting = config.getBoolean("siege.EjectionSettings.EjectDefendersDuringLooting");
        forbiddenSiegeCommands = config.getStringList("siege.StopUsageDuringSiege");
        siegeExtends = config.getBoolean("siege.ExtendSiegeClaims.SiegesExtend");
        secondaryDefendersTriggerExtendSiege = config.getBoolean("siege.ExtendSiegeClaims.SecondaryDefendersAddSiegeClaims");
        attackersTriggerExtendSiege = config.getBoolean("siege.ExtendSiegeClaims.AttackersAddSiegeClaims");
        primaryDefenderClaimsWithPermsAdded = config.getBoolean("siege.ExtendSiegeClaims.ClaimsWithPermsAddedToSiege");
        SecondaryDeathAffectsTimerBy = config.getInt("siege.Times.ExtendSiegeTime.SecondaryDeathAffectsTimerBy");
        ClaimsAddedExtraTime = config.getBoolean("siege.Times.ExtendSiegeTime.ClaimsAddExtraTime");
        MinutesAddedPer1000ClaimBlocksAdded = config.getInt("siege.Times.ExtendSiegeTime.MinutesAddedPer1000ClaimBlocksAdded");



        siegeDoorsOpen *= 60;
        surrenderExtendsTimeBy *= 60;
        siegeAttackStage *= 60;
    }
}
