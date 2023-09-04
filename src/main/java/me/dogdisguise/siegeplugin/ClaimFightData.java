package me.dogdisguise.siegeplugin;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import org.bukkit.entity.Player;

public class ClaimFightData {
    public long claimId = 0;

    public DataManager dataManager = new DataManager();


    public FightData fightData = null;
    public Claim claim;

    public boolean canSiege(Player defender) {

        if (claimId == 0) return false;

        this.claim = dataManager.getClaim(this.claimId);

        if (this.claim.isAdminClaim()) return false;

        if (this.claim.checkPermission(defender, ClaimPermission.Access, null) != null) return false;

        return true;
    }


}
