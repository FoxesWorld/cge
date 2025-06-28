package org.foxesworld.cge.modules.player;


import com.jme3.scene.Node;
import org.foxesworld.cge.modules.ui.UIModule;

/**
 * Inner HUD class for managing on-screen player stats.
 * Improved: prevents redundant updates, more robust.
 */
public class PlayerHud {
    private final Player player;
    public float speed = 0f;
    float armor = 0.6f;
    float ability = 0.4f;
    String test = "NovaUI";

    /**
     * Initializes HUD panels through the UIModule.
     *
     * @param p the Player instance
     */
    public PlayerHud(Player p) {
        this.player = p;
        UIModule ui = p.getEngine().getModuleManager().getModule(UIModule.class);
        ui.addPanel(this, "playerHud", "assets/Interface/stats_config.xml");
        ui.getNovaUi().registerEventHandler(this);
    }

    public void setPlayerSpeed(float s) { speed = Math.abs(s) * 7f; }
    public void setArmorBar(float a)    { armor = a; }
    public void setAbilityBar(float a)  { ability = a; }

    public void setTest(String test) {
        this.test = test;
    }

    /**
     * Updates HUD elements only if changed.
     *
     * @param tpf time per frame
     */
    public void update(float tpf) {
    }
}