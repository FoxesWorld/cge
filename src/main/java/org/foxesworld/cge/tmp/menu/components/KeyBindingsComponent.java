package org.foxesworld.cge.tmp.menu.components;

import com.atr.jme.font.shape.TrueTypeContainer;
import com.jme3.asset.AssetManager;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.RawInputListener;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Quad;
import org.foxesworld.cge.tmp.menu.BuildContext;
import org.foxesworld.cge.tmp.menu.components.utils.InteractiveComponent;
import org.foxesworld.cge.tmp.menu.input.KeyBindingsManager;
import org.foxesworld.cge.tmp.menu.xml.TextXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * KeyBindingsComponent — отображает список привязок клавиш и позволяет пользователю переназначать их.
 * Компонент поддерживает навигацию с помощью клавиатуры (стрелки вверх/вниз, Enter)
 * и полное управление через мышь.
 */
public final class KeyBindingsComponent extends UIComponent implements InteractiveComponent {

    // region Constants
    private static final Logger LOG = LoggerFactory.getLogger(KeyBindingsComponent.class);

    private static final ColorRGBA ROW_BG_NORMAL = new ColorRGBA(0.06f, 0.06f, 0.08f, 0.9f);
    private static final ColorRGBA ROW_BG_SELECTED = new ColorRGBA(0.12f, 0.12f, 0.16f, 0.9f);
    private static final String DEFAULT_KEY_TEXT = "-";
    private static final String REMAP_PROMPT_TEXT = "Press any key...";
    // endregion

    // region Fields
    private final BuildContext buildContext;
    private final AssetManager assetManager;
    private final InputManager inputManager;
    private final KeyBindingsManager manager;

    private final Node container = new Node("KeyBindingsContainer");

    // Layout & Style (DPI-aware)
    private float contentWidth = 640f;
    private float contentHeight = 300f;
    private float rowHeight = 40f;
    private float spacing = 6f;
    private float labelColWidth = 420f;
    private float buttonColWidth = 160f;
    private float margin = 12f;
    private float rowPadding = 5f; // Vertical padding inside a row

    // Materials
    private final Material rowMatNormal;
    private final Material rowMatSelected;

    // Rows in visual order (LinkedHashMap preserves insertion order)
    private final Map<String, Row> rows = new LinkedHashMap<>();

    // State for active remapping
    private RawInputListener remapListener = null;
    private String listeningBindId = null;

    // State for keyboard navigation
    private RawInputListener navListener = null;
    private int selectedRowIndex = -1;
    // endregion

    // region Inner Classes
    /**
     * Внутренний класс для хранения всех узлов, составляющих одну строку в списке.
     */
    private static final class Row {
        final KeyBindingsManager.KeyBind bind;
        final Geometry background;
        final ViceText actionLabel;
        final ViceButton remapButton;

        Row(KeyBindingsManager.KeyBind bind, Geometry background, ViceText actionLabel, ViceButton remapButton) {
            this.bind = bind;
            this.background = background;
            this.actionLabel = actionLabel;
            this.remapButton = remapButton;
        }
    }
    // endregion

    // region Initialization
    public KeyBindingsComponent(String id, BuildContext buildContext, KeyBindingsManager manager) {
        super(id);
        this.buildContext = Objects.requireNonNull(buildContext, "BuildContext cannot be null");
        this.assetManager = buildContext.mainMenuAppState().getGameEngine().getAssetManager();
        this.inputManager = buildContext.mainMenuAppState().getApplication().getInputManager();
        this.manager = Objects.requireNonNull(manager, "KeyBindingsManager cannot be null");

        // Pre-create materials to avoid cloning in update loops
        this.rowMatNormal = createMat(ROW_BG_NORMAL);
        this.rowMatSelected = createMat(ROW_BG_SELECTED);

        // Attach main container to this component's node
        getNode().attachChild(container);

        // Prepare listeners (but don't attach them yet)
        prepareRemapListener();
        prepareNavListener();

        // Build the initial UI from the manager
        rebuildRows();
    }

    private Material createMat(ColorRGBA color) {
        Material m = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        m.setColor("Color", color);
        m.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        return m;
    }
    // endregion

    // region Public API
    /**
     * Добавляет одну привязку в список. Используется для пошагового построения.
     * Если привязка с таким ID уже существует, она будет проигнорирована.
     * @param kb Объект привязки ключа.
     */
    public void addBinding(KeyBindingsManager.KeyBind kb) {
        if (kb == null || rows.containsKey(kb.id)) {
            return;
        }

        // Action Text Label (e.g., "Jump", "Move Forward")
        ViceText actionText = new ViceText(buildContext, new TextXml(kb.action != null && !kb.action.isEmpty() ? kb.action : kb.id));
        container.attachChild(actionText);

        // Remap Button (shows current key and is clickable)
        ViceButton remapButton = new ViceButton("kb-remap-" + kb.id, assetManager,
                getCurrentKeyName(kb),
                ViceButton.Style.getViceStyle(),
                () -> beginListening(kb.id), // Action on click
                null, 0);
        container.attachChild(remapButton.getNode());

        // Background Quad for the row
        Quad bgQuad = new Quad(1, 1); // Size will be set in finalizeLayout
        Geometry background = new Geometry("kb-row-bg-" + kb.id, bgQuad);
        background.setMaterial(rowMatNormal);
        container.attachChild(background);

        Row row = new Row(kb, background, actionText, remapButton);
        rows.put(kb.id, row);
    }

    /**
     * Полностью перестраивает UI, удаляя все старые строки и создавая новые
     * на основе текущего состояния KeyBindingsManager.
     */
    public void rebuildRows() {
        cleanupRows();

        for (KeyBindingsManager.KeyBind kb : manager.getAllBinds()) {
            addBinding(kb);
        }

        // Reset selection to the first row if available
        selectedRowIndex = rows.isEmpty() ? -1 : 0;
        highlightSelection();

        // After rebuilding, we need to recalculate the layout
        finalizeLayout(this.contentWidth, this.contentHeight);
    }

    /**
     * Обновляет тексты на кнопках для всех строк в соответствии с текущими значениями в менеджере.
     * Гораздо эффективнее, чем rebuildRows(), когда нужно только обновить отображаемые клавиши.
     */
    public void updateAllBindingsVisuals() {
        for (Row row : rows.values()) {
            updateRowVisuals(row);
        }
    }
    // endregion

    // region Layout & Sizing
    /**
     * Рассчитывает размеры и положения всех элементов на основе общих размеров компонента.
     * Этот метод учитывает DPI-масштабирование.
     */
    public void finalizeLayout(float totalWidth, float totalHeight) {
        float dpi = Math.max(1f, dpiScale);
        this.contentWidth = Math.max(16f, totalWidth);
        this.contentHeight = Math.max(48f, totalHeight);

        // Scale layout constants based on DPI
        this.rowHeight = FastMath.clamp(40f * dpi, 28f, 120f);
        this.spacing = Math.max(2f, 6f * dpi);
        this.margin = Math.max(6f, 12f * dpi);
        this.rowPadding = Math.max(2f, 5f * dpi);

        // Compute column widths
        float minButtonW = 80f * dpi;
        this.buttonColWidth = FastMath.clamp(160f * dpi, minButtonW, contentWidth * 0.4f);
        this.labelColWidth = Math.max(100f, contentWidth - buttonColWidth - margin * 2f);

        // Place rows with new sizes
        int idx = 0;
        for (Row r : rows.values()) {
            float rowY = -(idx * (rowHeight + spacing));

            // Update background quad
            Quad q = (Quad) r.background.getMesh();
            q.updateGeometry(contentWidth, rowHeight);
            r.background.setLocalTranslation(0f, rowY - rowHeight, 0.05f);

            // Position action label (vertically centered)
            float labelY = rowY - (rowHeight / 2f) - (r.actionLabel.getHeight() / 2f);
            r.actionLabel.setLocalTranslation(margin, labelY, 0.1f);

            // Position remap button
            float buttonHeight = rowHeight - 2 * rowPadding;
            float buttonWidth = buttonColWidth - 2 * rowPadding;
            r.remapButton.setSize(buttonWidth, buttonHeight);
            float buttonX = contentWidth - margin - buttonColWidth + rowPadding;
            float buttonY = rowY - rowHeight + rowPadding;
            r.remapButton.setPosition(buttonX, buttonY);

            idx++;
        }
    }

    @Override
    public void setSize(float width, float height) {
        finalizeLayout(width, height);
    }

    @Override
    public float getWidth() { return contentWidth; }

    @Override
    public float getHeight() { return Math.max(contentHeight, rows.size() * (rowHeight + spacing) - spacing); }
    // endregion

    // region Input Handling (Remapping)
    private void prepareRemapListener() {
        remapListener = new RawInputListener() {
            @Override public void onKeyEvent(KeyInputEvent evt) {
                if (evt.isPressed()) {
                    onCapturedKey(evt.getKeyCode());
                }
            }
            // Ignore other events
            @Override public void beginInput() {}
            @Override public void endInput() {}
            @Override public void onJoyAxisEvent(com.jme3.input.event.JoyAxisEvent evt) {}
            @Override public void onJoyButtonEvent(com.jme3.input.event.JoyButtonEvent evt) {}
            @Override public void onMouseMotionEvent(com.jme3.input.event.MouseMotionEvent evt) {}
            @Override public void onMouseButtonEvent(com.jme3.input.event.MouseButtonEvent evt) {}
            @Override public void onTouchEvent(com.jme3.input.event.TouchEvent evt) {}
        };
    }

    private void beginListening(String bindId) {
        stopRemapListening(); // Ensure no other listener is active
        this.listeningBindId = bindId;

        Row r = rows.get(bindId);
        if (r != null) {
            r.remapButton.setLabel(REMAP_PROMPT_TEXT);
            r.remapButton.setSelected(true);
        }

        try {
            inputManager.addRawInputListener(remapListener);
        } catch (Exception ex) {
            LOG.warn("Failed to register remap raw listener", ex);
            stopRemapListening();
        }
    }

    private void stopRemapListening() {
        if (remapListener != null) {
            try {
                inputManager.removeRawInputListener(remapListener);
            } catch (Exception ex) {
                // Might happen on shutdown, safe to ignore
            }
        }
        if (listeningBindId != null) {
            Row r = rows.get(listeningBindId);
            if (r != null) {
                // Restore button state
                r.remapButton.setLabel(getCurrentKeyName(r.bind));
                r.remapButton.setSelected(false);
            }
            listeningBindId = null;
        }
    }

    private void onCapturedKey(int keyCode) {
        if (listeningBindId == null) return;

        // Pressing Escape cancels the rebind process
        if (keyCode == KeyInput.KEY_ESCAPE) {
            stopRemapListening();
            return;
        }

        boolean success = manager.rebind(listeningBindId, keyCode, true); // Try to swap
        if (!success) {
            LOG.warn("Rebind with swap failed for {} -> {}. Trying without swap.", listeningBindId, keyCode);
            manager.rebind(listeningBindId, keyCode, false); // Fallback to overwrite
        }

        manager.saveToPreferences();
        manager.applyAllToInputManager();

        // Stop listening BEFORE updating visuals, to correctly restore the button label
        stopRemapListening();

        // Update UI for all rows, as a swap might have affected another binding
        updateAllBindingsVisuals();
    }
    // endregion

    // region Input Handling (Navigation & Interaction)
    private void prepareNavListener() {
        navListener = new RawInputListener() {
            @Override public void onKeyEvent(KeyInputEvent evt) {
                if (!evt.isPressed()) return;

                // If we are currently remapping a key, navigation is disabled, only remap listener works
                if (listeningBindId != null) {
                    onCapturedKey(evt.getKeyCode()); // Pass key to remapper
                    return;
                }

                switch (evt.getKeyCode()) {
                    case KeyInput.KEY_UP:
                        moveSelection(-1);
                        break;
                    case KeyInput.KEY_DOWN:
                        moveSelection(1);
                        break;
                    case KeyInput.KEY_RETURN:
                    case KeyInput.KEY_NUMPADENTER:
                        triggerSelected();
                        break;
                }
            }
            // Ignore other events
            @Override public void beginInput() {}
            @Override public void endInput() {}
            @Override public void onJoyAxisEvent(com.jme3.input.event.JoyAxisEvent evt) {}
            @Override public void onJoyButtonEvent(com.jme3.input.event.JoyButtonEvent evt) {}
            @Override public void onMouseMotionEvent(com.jme3.input.event.MouseMotionEvent evt) {}
            @Override public void onMouseButtonEvent(com.jme3.input.event.MouseButtonEvent evt) { if (evt.isPressed() && evt.getButtonIndex() == 0) { stopRemapListening(); } }
            @Override public void onTouchEvent(com.jme3.input.event.TouchEvent evt) {}
        };
    }

    private void enableNav(boolean enable) {
        try {
            if (enable) {
                inputManager.addRawInputListener(navListener);
            } else {
                inputManager.removeRawInputListener(navListener);
            }
        } catch (Exception ex) {
            LOG.warn("Failed to toggle navigation listener", ex);
        }
    }

    private void moveSelection(int delta) {
        if (rows.isEmpty()) {
            selectedRowIndex = -1;
            return;
        }
        int numRows = rows.size();
        int newIndex = (selectedRowIndex < 0) ? 0 : (selectedRowIndex + delta + numRows) % numRows;

        if (newIndex != selectedRowIndex) {
            selectedRowIndex = newIndex;
            highlightSelection();
        }
    }

    private void highlightSelection() {
        if (rows.isEmpty()) return;

        int idx = 0;
        for (Row r : rows.values()) {
            boolean isSelected = (idx == selectedRowIndex);
            r.background.setMaterial(isSelected ? rowMatSelected : rowMatNormal);
            r.remapButton.setHovered(isSelected); // Also visually mark the button
            idx++;
        }
    }

    private void triggerSelected() {
        if (selectedRowIndex < 0 || selectedRowIndex >= rows.size()) return;

        // Find the selected row and simulate a click on its button
        int idx = 0;
        for (Row r : rows.values()) {
            if (idx == selectedRowIndex) {
                r.remapButton.executeAction();
                return;
            }
            idx++;
        }
    }

    @Override
    public void update(float tpf) {
        for (Row r : rows.values()) {
            try {
                r.remapButton.update(tpf);
            } catch (Exception e) {
                LOG.error("Error updating ViceButton in KeyBindingsComponent", e);
            }
        }
    }

    public void handleMouseMove(Vector2f cursor) {
        int hoverIndex = -1;
        int idx = 0;
        for (Row r : rows.values()) {
            if (r.remapButton.intersects(cursor)) {
                hoverIndex = idx;
                break;
            }
            idx++;
        }

        // Update selection only if it changed to avoid unnecessary work
        if (hoverIndex != selectedRowIndex) {
            selectedRowIndex = hoverIndex;
            highlightSelection();
        }
    }

    @Override
    public void handleMousePress(Vector2f cursor) {
        for (Row r : rows.values()) {
            if (r.remapButton.intersects(cursor)) {
                r.remapButton.executeAction();
                return; // Only one action per click
            }
        }
    }

    @Override
    public void handleMouseDrag(Vector2f cursor) { /* Not needed */ }

    @Override
    public void handleMouseRelease() { /* Not needed */ }

    @Override
    public boolean intersects(Vector2f cursor) {
        if (!getNode().getCullHint().equals(Spatial.CullHint.Never)) return false;

        Vector2f worldPos = new Vector2f(getWorldTranslation().x, getWorldTranslation().y);
        float totalHeight = rows.size() * (rowHeight + spacing) - spacing;
        return cursor.x >= worldPos.x && cursor.x <= worldPos.x + contentWidth &&
                cursor.y <= worldPos.y && cursor.y >= worldPos.y - totalHeight;
    }

    @Override
    public void setActive(boolean active) {
        if (!active) {
            stopRemapListening();
        }
        enableNav(active);
    }

    @Override
    public void setHovered(boolean hovered) { /* This component manages its own hover state internally */ }
    // endregion

    // region Cleanup & Helpers
    public void cleanup() {
        setActive(false); // This will stop listeners
        cleanupRows();
        safeDetach(container);
    }

    private void cleanupRows() {
        for (Row r : rows.values()) {
            safeDetach(r.background);
            safeDetach(r.actionLabel);
            safeDetach(r.remapButton.getNode());
        }
        rows.clear();
    }

    private void updateRowVisuals(Row row) {
        try {
            row.remapButton.setLabel(getCurrentKeyName(row.bind));
        } catch (Exception e) {
            LOG.warn("Failed to update remap button label for bind: {}", row.bind.id, e);
        }
    }

    private String getCurrentKeyName(KeyBindingsManager.KeyBind kb) {
        String name = KeyBindingsManager.keyCodeToName(kb.getCurrentKeyCode());
        if (name == null || name.isEmpty()) {
            name = (kb.defaultKey != null && !kb.defaultKey.isEmpty()) ? kb.defaultKey : DEFAULT_KEY_TEXT;
        }
        return name;
    }

    private void safeDetach(Spatial s) {
        if (s != null && s.getParent() != null) {
            s.getParent().detachChild(s);
        }
    }
    // endregion
}