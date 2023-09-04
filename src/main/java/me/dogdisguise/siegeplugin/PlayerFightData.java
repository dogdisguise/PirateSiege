package me.dogdisguise.siegeplugin;

import java.util.UUID;

public class PlayerFightData {
    public FightData fightData = null;
    public UUID playerID;
    public boolean bypassSiegeTeleport = false;
    public long lastFightEndTimeStamp = 0;
    public PlayerFightData() {
    }

}
