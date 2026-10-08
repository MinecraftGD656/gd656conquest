package org.mods.gd656conquest.server.data.serverconfig;

import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomVehicleBackend;

public final class ServerConfigDataModel {
    private Boolean boolOverworldInvulnerable = true;
    private Boolean boolRestrictConquestDimension = true;
    private Boolean boolNaturalRegeneration = false;
    private Integer passiveRegenIntervalTicks = 3;
    private Integer passiveRegenStartDelaySeconds = 6;
    private Double maxScopePistol = 2.0;
    private Double maxScopeSniper = 12.0;
    private Double maxScopeRifle = 6.0;
    private Double maxScopeShotgun = 2.0;
    private Double maxScopeSmg = 3.0;
    private Double maxScopeRpg = 1.5;
    private Double maxScopeMg = 4.0;
    private RoomPosition overworldSpawn = new RoomPosition(0, 100, 0);
    private RoomPosition rangeJoinSpawn = new RoomPosition(0, 100, 0);
    private RoomPosition rangeLeaveSpawn = new RoomPosition(0, 100, 0);
    private RoomPosition parkourStartPos = new RoomPosition(0, 100, 0);
    private RoomPosition parkourEndPos = new RoomPosition(0, 100, 0);
    private String parkourCompletionTitleId = "";
    private String parkourBestTitleId = "";
    private RoomVehicleBackend vehicleBackend = RoomVehicleBackend.defaultValue();
    private Double missileKillRadius = 35.0;
    private Double groundSensorScanRadius = 10.0;
    private Integer groundSensorScanIntervalSeconds = 4;
    private Integer groundSensorMarkDurationSeconds = 2;
    private Integer groundSensorScanBonusCooldownSeconds = 10;
    private Boolean boolEnableExperimentalPlayerBalance = false;
    private Boolean boolMaintenanceMode = false;
    /**
     * GD656Mods: 玩家经验加成倍率 —— 对局结束结算经验时按此倍率乘算, 默认 1.0(不加成)。
     * 不是 1 时会在结束总结的「分数加成」「额外加成」两行后追加「(<活动介绍> ×倍率)」。
     */
    private Double playerExperienceMultiplier = 1.0D;
    /** GD656Mods: 经验加成活动介绍 —— 结束总结里那段「(<text> ×y)」的文本; 留空则括号内只显示倍率。 */
    private String experienceBonusEventDescription = "";
    /**
     * GD656Mods: 禁用全部载具的人数阈值 —— -1(默认)表示不限制。
     * 设为 x 时, 服务器在线人数 > x 会让所有房间的载具一律被视为"未启用"
     * (效果等同把载具配置的 enabled 切成 false: 不建运行时记录、列表不显示、不可部署)。
     */
    private Integer disableAllVehiclesPlayerThreshold = -1;
    /** GD656Mods: 玩家等级上限(默认 100, 可配置 1..1024)。 */
    private Integer maxPlayerLevel = 100;
    private Integer droneBatteryDurationSeconds = 60;
    private Integer kamikazeDroneBatteryDurationSeconds = 30;
    // GD656Mods: 弹药包单次补给数量(自己用组: 拾取弹药包掉落物)
    private Integer ammoBagTaczPistol = 6;
    private Integer ammoBagTaczSmg = 15;
    private Integer ammoBagTaczRifle = 10;
    private Integer ammoBagTaczSniper = 3;
    private Integer ammoBagTaczShotgun = 4;
    private Integer ammoBagTaczMg = 20;
    private Integer ammoBagTaczRpg = 1;
    private Integer ammoBagSbw = 1;
    // GD656Mods: 弹药包单次补给数量(支援组: 自己使用/给予他人弹药包)
    private Integer supportAmmoBagTaczPistol = 6;
    private Integer supportAmmoBagTaczSmg = 15;
    private Integer supportAmmoBagTaczRifle = 10;
    private Integer supportAmmoBagTaczSniper = 3;
    private Integer supportAmmoBagTaczShotgun = 4;
    private Integer supportAmmoBagTaczMg = 20;
    private Integer supportAmmoBagTaczRpg = 1;
    private Integer supportAmmoBagSbw = 1;
    /** GD656Mods: 最大活跃(进行中)房间数 —— 负数(默认 -1)表示不限制, 0 表示不允许任何对局。 */
    private Integer maxActiveRooms = -1;

    public Boolean getBoolOverworldInvulnerable() {
        return boolOverworldInvulnerable;
    }

    public void setBoolOverworldInvulnerable(Boolean boolOverworldInvulnerable) {
        this.boolOverworldInvulnerable = boolOverworldInvulnerable;
    }

    public Boolean getBoolRestrictConquestDimension() {
        return boolRestrictConquestDimension;
    }

    public void setBoolRestrictConquestDimension(Boolean boolRestrictConquestDimension) {
        this.boolRestrictConquestDimension = boolRestrictConquestDimension;
    }

    public Boolean getBoolNaturalRegeneration() {
        return boolNaturalRegeneration;
    }

    public void setBoolNaturalRegeneration(Boolean boolNaturalRegeneration) {
        this.boolNaturalRegeneration = boolNaturalRegeneration;
    }

    public Integer getPassiveRegenIntervalTicks() {
        return passiveRegenIntervalTicks;
    }

    public void setPassiveRegenIntervalTicks(Integer passiveRegenIntervalTicks) {
        this.passiveRegenIntervalTicks = passiveRegenIntervalTicks;
    }

    public Integer getPassiveRegenStartDelaySeconds() {
        return passiveRegenStartDelaySeconds;
    }

    public void setPassiveRegenStartDelaySeconds(Integer passiveRegenStartDelaySeconds) {
        this.passiveRegenStartDelaySeconds = passiveRegenStartDelaySeconds;
    }

    /** GD656Mods: 最大活跃(进行中)房间数; 0 表示不限制。 */
    public Integer getMaxActiveRooms() {
        return maxActiveRooms;
    }

    public void setMaxActiveRooms(Integer maxActiveRooms) {
        this.maxActiveRooms = maxActiveRooms;
    }

    public Double getMaxScopePistol() {
        return maxScopePistol;
    }

    public void setMaxScopePistol(Double maxScopePistol) {
        this.maxScopePistol = maxScopePistol;
    }

    public Double getMaxScopeSniper() {
        return maxScopeSniper;
    }

    public void setMaxScopeSniper(Double maxScopeSniper) {
        this.maxScopeSniper = maxScopeSniper;
    }

    public Double getMaxScopeRifle() {
        return maxScopeRifle;
    }

    public void setMaxScopeRifle(Double maxScopeRifle) {
        this.maxScopeRifle = maxScopeRifle;
    }

    public Double getMaxScopeShotgun() {
        return maxScopeShotgun;
    }

    public void setMaxScopeShotgun(Double maxScopeShotgun) {
        this.maxScopeShotgun = maxScopeShotgun;
    }

    public Double getMaxScopeSmg() {
        return maxScopeSmg;
    }

    public void setMaxScopeSmg(Double maxScopeSmg) {
        this.maxScopeSmg = maxScopeSmg;
    }

    public Double getMaxScopeRpg() {
        return maxScopeRpg;
    }

    public void setMaxScopeRpg(Double maxScopeRpg) {
        this.maxScopeRpg = maxScopeRpg;
    }

    public Double getMaxScopeMg() {
        return maxScopeMg;
    }

    public void setMaxScopeMg(Double maxScopeMg) {
        this.maxScopeMg = maxScopeMg;
    }

    public RoomPosition getOverworldSpawn() {
        return overworldSpawn;
    }

    public void setOverworldSpawn(RoomPosition overworldSpawn) {
        this.overworldSpawn = overworldSpawn;
    }

    public RoomPosition getRangeJoinSpawn() {
        return rangeJoinSpawn;
    }

    public void setRangeJoinSpawn(RoomPosition rangeJoinSpawn) {
        this.rangeJoinSpawn = rangeJoinSpawn;
    }

    public RoomPosition getRangeLeaveSpawn() {
        return rangeLeaveSpawn;
    }

    public void setRangeLeaveSpawn(RoomPosition rangeLeaveSpawn) {
        this.rangeLeaveSpawn = rangeLeaveSpawn;
    }

    public RoomPosition getParkourStartPos() {
        return parkourStartPos;
    }

    public void setParkourStartPos(RoomPosition parkourStartPos) {
        this.parkourStartPos = parkourStartPos;
    }

    public RoomPosition getParkourEndPos() {
        return parkourEndPos;
    }

    public void setParkourEndPos(RoomPosition parkourEndPos) {
        this.parkourEndPos = parkourEndPos;
    }

    public String getParkourCompletionTitleId() {
        return parkourCompletionTitleId;
    }

    public void setParkourCompletionTitleId(String parkourCompletionTitleId) {
        this.parkourCompletionTitleId = parkourCompletionTitleId;
    }

    public String getParkourBestTitleId() {
        return parkourBestTitleId;
    }

    public void setParkourBestTitleId(String parkourBestTitleId) {
        this.parkourBestTitleId = parkourBestTitleId;
    }

    public RoomVehicleBackend getVehicleBackend() {
        return vehicleBackend;
    }

    public void setVehicleBackend(RoomVehicleBackend vehicleBackend) {
        this.vehicleBackend = vehicleBackend;
    }

    public Double getMissileKillRadius() {
        return missileKillRadius;
    }

    public void setMissileKillRadius(Double missileKillRadius) {
        this.missileKillRadius = missileKillRadius;
    }

    public Double getGroundSensorScanRadius() {
        return groundSensorScanRadius;
    }

    public void setGroundSensorScanRadius(Double groundSensorScanRadius) {
        this.groundSensorScanRadius = groundSensorScanRadius;
    }

    public Integer getGroundSensorScanIntervalSeconds() {
        return groundSensorScanIntervalSeconds;
    }

    public void setGroundSensorScanIntervalSeconds(Integer groundSensorScanIntervalSeconds) {
        this.groundSensorScanIntervalSeconds = groundSensorScanIntervalSeconds;
    }

    public Integer getGroundSensorMarkDurationSeconds() {
        return groundSensorMarkDurationSeconds;
    }

    public void setGroundSensorMarkDurationSeconds(Integer groundSensorMarkDurationSeconds) {
        this.groundSensorMarkDurationSeconds = groundSensorMarkDurationSeconds;
    }

    public Integer getGroundSensorScanBonusCooldownSeconds() {
        return groundSensorScanBonusCooldownSeconds;
    }

    public void setGroundSensorScanBonusCooldownSeconds(Integer groundSensorScanBonusCooldownSeconds) {
        this.groundSensorScanBonusCooldownSeconds = groundSensorScanBonusCooldownSeconds;
    }

    public Boolean getBoolEnableExperimentalPlayerBalance() {
        return boolEnableExperimentalPlayerBalance;
    }

    public void setBoolEnableExperimentalPlayerBalance(Boolean boolEnableExperimentalPlayerBalance) {
        this.boolEnableExperimentalPlayerBalance = boolEnableExperimentalPlayerBalance;
    }

    public Boolean getBoolMaintenanceMode() {
        return boolMaintenanceMode;
    }

    public void setBoolMaintenanceMode(Boolean boolMaintenanceMode) {
        this.boolMaintenanceMode = boolMaintenanceMode;
    }

    public Integer getDroneBatteryDurationSeconds() {
        return droneBatteryDurationSeconds;
    }

    public void setDroneBatteryDurationSeconds(Integer droneBatteryDurationSeconds) {
        this.droneBatteryDurationSeconds = droneBatteryDurationSeconds;
    }

    public Integer getMaxPlayerLevel() {
        return maxPlayerLevel;
    }

    public void setMaxPlayerLevel(Integer maxPlayerLevel) {
        this.maxPlayerLevel = maxPlayerLevel;
    }

    public Integer getKamikazeDroneBatteryDurationSeconds() {
        return kamikazeDroneBatteryDurationSeconds;
    }

    public void setKamikazeDroneBatteryDurationSeconds(Integer kamikazeDroneBatteryDurationSeconds) {
        this.kamikazeDroneBatteryDurationSeconds = kamikazeDroneBatteryDurationSeconds;
    }

    public Integer getAmmoBagTaczPistol() {
        return ammoBagTaczPistol;
    }

    public void setAmmoBagTaczPistol(Integer ammoBagTaczPistol) {
        this.ammoBagTaczPistol = ammoBagTaczPistol;
    }

    public Integer getAmmoBagTaczSmg() {
        return ammoBagTaczSmg;
    }

    public void setAmmoBagTaczSmg(Integer ammoBagTaczSmg) {
        this.ammoBagTaczSmg = ammoBagTaczSmg;
    }

    public Integer getAmmoBagTaczRifle() {
        return ammoBagTaczRifle;
    }

    public void setAmmoBagTaczRifle(Integer ammoBagTaczRifle) {
        this.ammoBagTaczRifle = ammoBagTaczRifle;
    }

    public Integer getAmmoBagTaczSniper() {
        return ammoBagTaczSniper;
    }

    public void setAmmoBagTaczSniper(Integer ammoBagTaczSniper) {
        this.ammoBagTaczSniper = ammoBagTaczSniper;
    }

    public Integer getAmmoBagTaczShotgun() {
        return ammoBagTaczShotgun;
    }

    public void setAmmoBagTaczShotgun(Integer ammoBagTaczShotgun) {
        this.ammoBagTaczShotgun = ammoBagTaczShotgun;
    }

    public Integer getAmmoBagTaczMg() {
        return ammoBagTaczMg;
    }

    public void setAmmoBagTaczMg(Integer ammoBagTaczMg) {
        this.ammoBagTaczMg = ammoBagTaczMg;
    }

    public Integer getAmmoBagTaczRpg() {
        return ammoBagTaczRpg;
    }

    public void setAmmoBagTaczRpg(Integer ammoBagTaczRpg) {
        this.ammoBagTaczRpg = ammoBagTaczRpg;
    }

    public Integer getAmmoBagSbw() {
        return ammoBagSbw;
    }

    public void setAmmoBagSbw(Integer ammoBagSbw) {
        this.ammoBagSbw = ammoBagSbw;
    }

    public Integer getSupportAmmoBagTaczPistol() {
        return supportAmmoBagTaczPistol;
    }

    public void setSupportAmmoBagTaczPistol(Integer supportAmmoBagTaczPistol) {
        this.supportAmmoBagTaczPistol = supportAmmoBagTaczPistol;
    }

    public Integer getSupportAmmoBagTaczSmg() {
        return supportAmmoBagTaczSmg;
    }

    public void setSupportAmmoBagTaczSmg(Integer supportAmmoBagTaczSmg) {
        this.supportAmmoBagTaczSmg = supportAmmoBagTaczSmg;
    }

    public Integer getSupportAmmoBagTaczRifle() {
        return supportAmmoBagTaczRifle;
    }

    public void setSupportAmmoBagTaczRifle(Integer supportAmmoBagTaczRifle) {
        this.supportAmmoBagTaczRifle = supportAmmoBagTaczRifle;
    }

    public Integer getSupportAmmoBagTaczSniper() {
        return supportAmmoBagTaczSniper;
    }

    public void setSupportAmmoBagTaczSniper(Integer supportAmmoBagTaczSniper) {
        this.supportAmmoBagTaczSniper = supportAmmoBagTaczSniper;
    }

    public Integer getSupportAmmoBagTaczShotgun() {
        return supportAmmoBagTaczShotgun;
    }

    public void setSupportAmmoBagTaczShotgun(Integer supportAmmoBagTaczShotgun) {
        this.supportAmmoBagTaczShotgun = supportAmmoBagTaczShotgun;
    }

    public Integer getSupportAmmoBagTaczMg() {
        return supportAmmoBagTaczMg;
    }

    public void setSupportAmmoBagTaczMg(Integer supportAmmoBagTaczMg) {
        this.supportAmmoBagTaczMg = supportAmmoBagTaczMg;
    }

    public Integer getSupportAmmoBagTaczRpg() {
        return supportAmmoBagTaczRpg;
    }

    public void setSupportAmmoBagTaczRpg(Integer supportAmmoBagTaczRpg) {
        this.supportAmmoBagTaczRpg = supportAmmoBagTaczRpg;
    }

    public Integer getSupportAmmoBagSbw() {
        return supportAmmoBagSbw;
    }

    public void setSupportAmmoBagSbw(Integer supportAmmoBagSbw) {
        this.supportAmmoBagSbw = supportAmmoBagSbw;
    }

    /** GD656Mods: 玩家经验加成倍率(默认 1.0)。 */
    public Double getPlayerExperienceMultiplier() {
        return playerExperienceMultiplier;
    }

    public void setPlayerExperienceMultiplier(Double playerExperienceMultiplier) {
        this.playerExperienceMultiplier = playerExperienceMultiplier;
    }

    /** GD656Mods: 经验加成活动介绍(用于结束总结的括号文本)。 */
    public String getExperienceBonusEventDescription() {
        return experienceBonusEventDescription;
    }

    public void setExperienceBonusEventDescription(String experienceBonusEventDescription) {
        this.experienceBonusEventDescription = experienceBonusEventDescription;
    }

    /** GD656Mods: 禁用全部载具的人数阈值(-1 = 不限制)。 */
    public Integer getDisableAllVehiclesPlayerThreshold() {
        return disableAllVehiclesPlayerThreshold;
    }

    public void setDisableAllVehiclesPlayerThreshold(Integer disableAllVehiclesPlayerThreshold) {
        this.disableAllVehiclesPlayerThreshold = disableAllVehiclesPlayerThreshold;
    }
}
