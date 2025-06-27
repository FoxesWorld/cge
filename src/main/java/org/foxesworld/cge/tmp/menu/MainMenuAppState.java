package org.foxesworld.cge.tmp.menu;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.font.BitmapText;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.scene.Node;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.tmp.menu.layout.MenuLayoutLoader;
import org.foxesworld.cge.tmp.menu.layout.components.ElementLayout;
import org.foxesworld.cge.tmp.menu.layout.components.MenuComponentFactory;
import org.foxesworld.cge.tmp.menu.layout.components.elements.Button;
import org.foxesworld.cge.tmp.menu.layout.components.elements.Image;

import java.util.*;

/**
 * Main menu app state with animated background and XML-based layout.
 */
public class MainMenuAppState extends BaseAppState implements ActionListener {

    private CalistaGameEngine app;
    private Node guiNode;
    private MenuBackground background;
    private List<Button> buttons = new ArrayList<>();
    private int selectedIndex = 0;

    private float overlayAlpha = 0f;
    private boolean fadeIn = true;
    private float pulseTimer = 0f;

    private String layoutPath;
    private String keyMenuUp;
    private String keyMenuDown;
    private String keyMenuEnter;
    private ResourceBundle menuStrings;

    @Override
    protected void initialize(Application app) {
        this.app = (CalistaGameEngine) app;
        this.guiNode = this.app.getGuiNode();

        loadMenuConfig();

        background = new MenuBackground(this.app);
        guiNode.attachChild(background.getArt());
        guiNode.attachChild(background.getGeoGroup());
        guiNode.attachChild(background.getGeometry());

        // Load layout via XML
        MenuComponentFactory factory = new MenuComponentFactory(this.app);
        List<ElementLayout> layouts = MenuLayoutLoader.load(this.app, layoutPath);

        boolean hasLogo = false;

        for (ElementLayout layout : layouts) {
            Object comp = factory.createComponent(layout, this::getButtonAction);
            if (comp instanceof Button button) {
                buttons.add(button);
            } else if (comp instanceof Node node) {
                guiNode.attachChild(node);
            } else if (comp instanceof BitmapText text) {
                guiNode.attachChild(text);
            }
        }

        // fallback buttons
        if (buttons.isEmpty()) {
            addFallbackButtons();
        }

        placeButtons();

        setupInput();
    }

    /**
     * Вынесла добавление дефолтных кнопок в отдельный метод.
     */
    private void addFallbackButtons() {
        buttons.add(new Button(this.app, getString("menu.start", "Start Game"), () -> app.startGameFromMenu(),
                background.getMenuX() + 32, background.getMenuY() + 28, 320, 60));

        buttons.add(new Button(this.app, getString("menu.options", "Options"), () -> { /* options */ },
                background.getMenuX() + 32, background.getMenuY() + 98, 320, 60));

        buttons.add(new Button(this.app, getString("menu.exit", "Exit"), () -> app.stop(),
                background.getMenuX() + 32, background.getMenuY() + 168, 320, 60));
    }

    /**
     * Вынесла расстановку кнопок в отдельный метод.
     */
    private void placeButtons() {
        for (int i = 0; i < buttons.size(); i++) {
            Button btn = buttons.get(i);
            btn.setSelected(i == selectedIndex, getPulseColor());
            btn.place(btn.getX(), btn.getY(), i == selectedIndex);
            btn.attachTo(guiNode);
        }
    }

    /**
     * Настройка управления клавишами.
     */
    private void setupInput() {
        var input = app.getInputManager();
        input.addMapping(keyMenuUp, new KeyTrigger(KeyInput.KEY_UP));
        input.addMapping(keyMenuDown, new KeyTrigger(KeyInput.KEY_DOWN));
        input.addMapping(keyMenuEnter, new KeyTrigger(KeyInput.KEY_RETURN));
        input.addListener(this, keyMenuUp, keyMenuDown, keyMenuEnter);
    }

    private void loadMenuConfig() {
        this.layoutPath = "assets/Interface/MenuLayout.xml";
        this.keyMenuUp = "MenuUp";
        this.keyMenuDown = "MenuDown";
        this.keyMenuEnter = "MenuEnter";
        try {
            this.menuStrings = ResourceBundle.getBundle("MenuStrings");
        } catch (Exception e) {
            this.menuStrings = null;
        }
    }

    private String getString(String key, String defaultValue) {
        if (menuStrings != null && menuStrings.containsKey(key)) {
            return menuStrings.getString(key);
        }
        return defaultValue;
    }

    private Runnable getButtonAction(String id) {
        return switch (id) {
            case "btnStart"   -> () -> app.startGameFromMenu();
            case "btnOptions" -> () -> { /* options screen */ };
            case "btnExit"    -> () -> app.stop();
            default           -> () -> {};
        };
    }

    private void updateButtons() {
        for (int i = 0; i < buttons.size(); i++) {
            buttons.get(i).setSelected(i == selectedIndex, getPulseColor());
        }
    }

    private ColorRGBA getPulseColor() {
        float pulse = 0.5f + 0.5f * FastMath.sin(pulseTimer * 2.5f);
        return Button.pulseColor(pulse);
    }

    @Override
    public void update(float tpf) {
        if (fadeIn && overlayAlpha < MenuBackground.BG_COLOR.a) {
            overlayAlpha = FastMath.interpolateLinear(0.08f, overlayAlpha, MenuBackground.BG_COLOR.a);
            background.setAlpha(overlayAlpha);
            if (overlayAlpha >= MenuBackground.BG_COLOR.a * 0.95f) fadeIn = false;
        }

        pulseTimer += tpf;
        updateButtons();

        background.update(tpf);
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if (!isPressed || buttons.isEmpty()) return;

        if (name.equals(keyMenuUp)) {
            selectedIndex = (selectedIndex + 1) % buttons.size();
        } else if (name.equals(keyMenuDown)) {
            selectedIndex = (selectedIndex - 1 + buttons.size()) % buttons.size();
        } else if (name.equals(keyMenuEnter)) {
            buttons.get(selectedIndex).click();
        }

        updateButtons();
    }

    @Override
    protected void cleanup(Application app) {
        background.detach();
        for (Button b : buttons) b.detach();

        var input = app.getInputManager();
        input.deleteMapping(keyMenuUp);
        input.deleteMapping(keyMenuDown);
        input.deleteMapping(keyMenuEnter);
        input.removeListener(this);
    }

    @Override
    protected void onEnable() {}
    @Override
    protected void onDisable() {}
}
