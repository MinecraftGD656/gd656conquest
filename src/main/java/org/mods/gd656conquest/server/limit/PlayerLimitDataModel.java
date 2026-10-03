package org.mods.gd656conquest.server.limit;

/**
 * GD656Mods: 服务器人数限制的持久化模型。
 *
 * <p>独立存储于 {@code gd656conquest/playerlimit.json}(不复用 serverconfig.json)。
 * 字段一律用包装类型, 便于 Gson 反序列化后识别"缺失"(缺失时由 Store 的 normalize 补默认值)。
 */
public final class PlayerLimitDataModel {
    /** GD656Mods: 是否开启人数限制, 默认关闭。 */
    private Boolean enabled = false;
    /** GD656Mods: 计数上限, 默认 128。 */
    private Integer maxPlayers = 128;
    /** GD656Mods: 因超限被踢时玩家看到的提示。 */
    private String reason = "服务器已满！请稍后重试";

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(Integer maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
