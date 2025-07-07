package org.foxesworld.cge.modules.player.hud;

import org.foxesworld.cge.modules.player.Player;
import org.foxesworld.cge.modules.ui.UIModule;
import org.foxesworld.cge.modules.ui.novaUi.NovaUI;
import org.foxesworld.cge.modules.ui.novaUi.elements.image.ImageElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.panel.PanelElement;

/**
 * Manages the player's Heads-Up Display (HUD), showing player stats like speed, armor, etc.
 * The inventory display is handled by the separate InventoryUI AppState.
 */
public class PlayerHud {

    private final Player player;
    private final NovaUI hud;

    // --- HUD Fields for data binding from XML ---
    public float speed = 0f;
    public float armor = 0.6f;
    public float ability = 0.4f;

    /**
     * Initializes HUD panels from an XML configuration.
     * @param p The Player instance.
     */
    public PlayerHud(Player p) {
        this.player = p;
        UIModule ui = p.getEngine().getModuleManager().getModule(UIModule.class);
        this.hud = ui.createUi("hud", "assets/Interface/stats_config.xml", this);
        createCrosshair();
    }

    private void createCrosshair() {
        //ImageElement crosshair = new ImageElement(player.getEngine(), "crosshair", hud.getRootPanel());
        //crosshair.setProperty("imagepath", "assets/Interface/crosshair.png");
        //crosshair.setProperty("width", "32");
        //crosshair.setProperty("height", "32");
        //crosshair.setProperty("align", "center");
    }

    public void update(float tpf) {
        // Этот метод может остаться для будущих нужд,
        // но data-binding в NovaUI уже делает большую часть работы.
    }

    // --- Методы для обновления полей, используемых NovaUI из XML ---
    public void setPlayerSpeed(float s) { this.speed = Math.abs(s) * 7f; }
    public void setArmorBar(float a)    { this.armor = a; }
    public void setAbilityBar(float a)  { this.ability = a; }
}