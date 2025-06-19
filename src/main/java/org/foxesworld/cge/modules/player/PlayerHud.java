package org.foxesworld.cge.modules.player;


import org.foxesworld.cge.modules.ui.UIModule;

/**
 * Inner HUD class for managing on-screen player stats.
 * Improved: prevents redundant updates, more robust.
 */
public class PlayerHud {
    private float speed;
    private float armor = 0.6f;
    private float ability = 0.4f;
    private float prevSpeed = -1f, prevArmor = -1f, prevAbility = -1f;
    private final UIModule ui;

    /**
     * Initializes HUD panels through the UIModule.
     *
     * @param p the Player instance
     */
    public PlayerHud(Player p) {
        this.ui = p.getEngine().getModuleManager().getModule(UIModule.class);
        ui.addPanel(this, "assets/Interface/stats_config.xml");
    }

    public void setPlayerSpeed(float s) { speed = s; }
    public void setArmorBar(float a)    { armor = a; }
    public void setAbilityBar(float a)  { ability = a; }

    /**
     * Updates HUD elements only if changed.
     *
     * @param tpf time per frame
     */
    public void update(float tpf) {

    }
}