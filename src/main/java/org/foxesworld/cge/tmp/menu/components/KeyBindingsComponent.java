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
import com.jme3.math.Vector3f;
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
 * Исправления: корректное центрированное позиционирование строк, единая логика Y-координат,
 * безопасное центрирование текста, обновлён intersects().
 */
public final class KeyBindingsComponent extends UIComponent implements InteractiveComponent {

    private static final Logger LOG = LoggerFactory.getLogger(KeyBindingsComponent.class);

    private static final ColorRGBA ROW_BG_NORMAL = new ColorRGBA(0.06f, 0.06f, 0.08f, 0.9f);
    private static final ColorRGBA ROW_BG_SELECTED = new ColorRGBA(0.12f, 0.12f, 0.16f, 0.9f);
    private static final String DEFAULT_KEY_TEXT = "-";
    private static final String REMAP_PROMPT_TEXT = "Press any key...";

    private final BuildContext buildContext;
    private final AssetManager assetManager;
    private final InputManager inputManager;
    private final KeyBindingsManager manager;

    private final Node container = new Node("KeyBindingsContainer");

    // Layout & style (DPI-aware base values)
    private float contentWidth = 640f;
    private float contentHeight = 300f;
    private float rowHeight = 40f;
    private float spacing = 6f;
    private float labelColWidth = 420f;
    private float buttonColWidth = 160f;
    private float margin = 12f;
    private float rowPadding = 5f; // vertical padding inside a row

    // Materials
    private final Material rowMatNormal;
    private final Material rowMatSelected;

    // Rows in visual order
    private final Map<String, Row> rows = new LinkedHashMap<>();

    // Remap state
    private RawInputListener remapListener = null;
    private String listeningBindId = null;

    // Nav state
    private RawInputListener navListener = null;
    private int selectedRowIndex = -1;

    // Inner row descriptor
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

    public KeyBindingsComponent(String id, BuildContext buildContext, KeyBindingsManager manager) {
        super(id);
        this.buildContext = Objects.requireNonNull(buildContext, "BuildContext cannot be null");
        this.assetManager = buildContext.mainMenuAppState().getGameEngine().getAssetManager();
        this.inputManager = buildContext.mainMenuAppState().getApplication().getInputManager();
        this.manager = Objects.requireNonNull(manager, "KeyBindingsManager cannot be null");

        // Pre-create materials
        this.rowMatNormal = createMat(ROW_BG_NORMAL);
        this.rowMatSelected = createMat(ROW_BG_SELECTED);

        // attach container to this node (container children positioned relative to component origin)
        getNode().attachChild(container);

        // prepare listeners (not registered yet)
        prepareRemapListener();
        prepareNavListener();

        // build UI
        rebuildRows();
    }

    private Material createMat(ColorRGBA color) {
        Material m = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        m.setColor("Color", color);
        m.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        return m;
    }

    // ---------- Public API ----------

    public void addBinding(KeyBindingsManager.KeyBind kb) {
        if (kb == null || rows.containsKey(kb.id)) return;

        ViceText actionText = new ViceText(buildContext, new TextXml(kb.action != null && !kb.action.isEmpty() ? kb.action : kb.id));
        container.attachChild(actionText);

        ViceButton remapButton = new ViceButton("kb-remap-" + kb.id, assetManager,
                getCurrentKeyName(kb),
                ViceButton.Style.getViceStyle(),
                () -> beginListening(kb.id),
                null, 0);
        container.attachChild(remapButton.getNode());

        Quad bgQuad = new Quad(1f, 1f); // size set in finalizeLayout
        Geometry background = new Geometry("kb-row-bg-" + kb.id, bgQuad);
        background.setMaterial(rowMatNormal);
        container.attachChild(background);

        Row row = new Row(kb, background, actionText, remapButton);
        rows.put(kb.id, row);
    }

    public void rebuildRows() {
        cleanupRows();
        for (KeyBindingsManager.KeyBind kb : manager.getAllBinds()) {
            addBinding(kb);
        }
        selectedRowIndex = rows.isEmpty() ? -1 : 0;
        highlightSelection();
        finalizeLayout(contentWidth, contentHeight);
    }

    public void updateAllBindingsVisuals() {
        for (Row row : rows.values()) updateRowVisuals(row);
    }

    private String getCurrentKeyName(KeyBindingsManager.KeyBind kb) {
        String name = KeyBindingsManager.keyCodeToName(kb.getCurrentKeyCode());
        if (name == null || name.isEmpty()) name = (kb.defaultKey != null && !kb.defaultKey.isEmpty()) ? kb.defaultKey : DEFAULT_KEY_TEXT;
        return name;
    }

    private void updateRowVisuals(Row row) {
        try {
            row.remapButton.setLabel(getCurrentKeyName(row.bind));
        } catch (Exception e) {
            LOG.warn("Failed to update remap button label for bind: {}", row.bind.id, e);
        }
    }

    // ---------- Layout & sizing (fixed) ----------

    /**
     * Теперь компонент ориентирован относительно своего центра.
     * topStartY = contentHeight/2 - margin
     */
    public void finalizeLayout(float totalWidth, float totalHeight) {
        float dpi = Math.max(1f, dpiScale);
        this.contentWidth = Math.max(16f, totalWidth);
        this.contentHeight = Math.max(48f, totalHeight);

        this.rowHeight = FastMath.clamp(40f * dpi, 24f, 200f);
        this.spacing = Math.max(2f, 6f * dpi);
        this.margin = Math.max(4f, 12f * dpi);
        this.rowPadding = Math.max(2f, 5f * dpi);

        float minButtonW = 64f * dpi;
        this.buttonColWidth = FastMath.clamp(160f * dpi, minButtonW, contentWidth * 0.45f);
        this.labelColWidth = Math.max(80f, contentWidth - buttonColWidth - margin * 2f);

        // topStartY measured from component center (positive up)
        final float topStartY = contentHeight * 0.5f - margin;

        int idx = 0;
        for (Row r : rows.values()) {
            // top = topStartY - idx*(rowHeight+spacing)
            float top = topStartY - idx * (rowHeight + spacing);
            // actualY is bottom-left Y for Quad (since Quad spans from y..y+height)
            float actualY = top - rowHeight;

            // Update background size & pos
            Quad q = (Quad) r.background.getMesh();
            q.updateGeometry(contentWidth, rowHeight);
            // Z-order small positive value to be above background layer if needed
            r.background.setLocalTranslation(0f, actualY, 0.05f);

            // Determine label height (fallback if 0)
            float labelH;
            try {
                labelH = r.actionLabel.getHeight();
            } catch (Throwable t) {
                labelH = 0f;
            }
            if (labelH <= 0f) {
                // fallback: estimate label height as portion of rowHeight
                labelH = FastMath.clamp(rowHeight * 0.55f, 8f, rowHeight - 4f);
            }
            float labelY = actualY + (rowHeight * 0.5f) - (labelH * 0.5f);
            r.actionLabel.setLocalTranslation(margin, labelY, 0.1f);

            // Button placement: right-aligned, vertically centered
            float buttonH = rowHeight - 2f * rowPadding;
            float buttonW = buttonColWidth - 2f * rowPadding;
            r.remapButton.setSize(buttonW, buttonH);
            float buttonX = contentWidth - margin - buttonColWidth + rowPadding;
            float buttonY = actualY + (rowHeight * 0.5f) - (buttonH * 0.5f);
            r.remapButton.setPosition(buttonX, buttonY);

            idx++;
        }
    }

    @Override
    public void setSize(float width, float height) {
        finalizeLayout(width, height);
    }

    @Override
    public float getWidth() {
        return contentWidth;
    }

    @Override
    public float getHeight() {
        return contentHeight / 2;
    }

    // ---------- Remap handling ----------

    private void prepareRemapListener() {
        remapListener = new RawInputListener() {
            @Override public void beginInput() {}
            @Override public void endInput() {}
            @Override public void onJoyAxisEvent(com.jme3.input.event.JoyAxisEvent evt) {}
            @Override public void onJoyButtonEvent(com.jme3.input.event.JoyButtonEvent evt) {}
            @Override public void onMouseMotionEvent(com.jme3.input.event.MouseMotionEvent evt) {}
            @Override public void onMouseButtonEvent(com.jme3.input.event.MouseButtonEvent evt) {}
            @Override public void onKeyEvent(KeyInputEvent evt) {
                if (!evt.isPressed()) return;
                onCapturedKey(evt.getKeyCode());
            }
            @Override public void onTouchEvent(com.jme3.input.event.TouchEvent evt) {}
        };
    }

    private void beginListening(String bindId) {
        stopRemapListening();
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
        try {
            inputManager.removeRawInputListener(remapListener);
        } catch (Exception ignored) {}
        if (listeningBindId != null) {
            Row r = rows.get(listeningBindId);
            if (r != null) {
                r.remapButton.setSelected(false);
                r.remapButton.setLabel(getCurrentKeyName(r.bind));
            }
            listeningBindId = null;
        }
    }

    private void onCapturedKey(int keyCode) {
        if (listeningBindId == null) return;
        if (keyCode == KeyInput.KEY_ESCAPE) { stopRemapListening(); return; }

        boolean ok = manager.rebind(listeningBindId, keyCode, true);
        if (!ok) {
            LOG.warn("Rebind failed (swap) for {} -> {}; trying non-swap", listeningBindId, keyCode);
            manager.rebind(listeningBindId, keyCode, false);
        }
        manager.saveToFile();
        manager.applyAllToInputManager();

        stopRemapListening();
        updateAllBindingsVisuals();
    }

    // ---------- Navigation ----------

    private void prepareNavListener() {
        navListener = new RawInputListener() {
            @Override public void beginInput() {}
            @Override public void endInput() {}
            @Override public void onJoyAxisEvent(com.jme3.input.event.JoyAxisEvent evt) {}
            @Override public void onJoyButtonEvent(com.jme3.input.event.JoyButtonEvent evt) {}
            @Override public void onMouseMotionEvent(com.jme3.input.event.MouseMotionEvent evt) {}
            @Override public void onMouseButtonEvent(com.jme3.input.event.MouseButtonEvent evt) {
                if (evt.isPressed() && evt.getButtonIndex() == 0) stopRemapListening();
            }
            @Override public void onKeyEvent(KeyInputEvent evt) {
                if (!evt.isPressed()) return;
                if (listeningBindId != null) {
                    onCapturedKey(evt.getKeyCode());
                    return;
                }
                int kc = evt.getKeyCode();
                if (kc == KeyInput.KEY_UP) moveSelection(-1);
                else if (kc == KeyInput.KEY_DOWN) moveSelection(1);
                else if (kc == KeyInput.KEY_RETURN || kc == KeyInput.KEY_NUMPADENTER) triggerSelected();
                else if (kc == KeyInput.KEY_ESCAPE) stopRemapListening();
            }
            @Override public void onTouchEvent(com.jme3.input.event.TouchEvent evt) {}
        };
    }

    private void enableNav(boolean enable) {
        try {
            if (enable) inputManager.addRawInputListener(navListener);
            else inputManager.removeRawInputListener(navListener);
        } catch (Exception ex) {
            LOG.warn("Failed to toggle navigation listener", ex);
        }
    }

    private void moveSelection(int delta) {
        if (rows.isEmpty()) { selectedRowIndex = -1; return; }
        int n = rows.size();
        if (selectedRowIndex < 0) selectedRowIndex = 0;
        selectedRowIndex = (selectedRowIndex + delta + n) % n;
        highlightSelection();
    }

    private void highlightSelection() {
        if (rows.isEmpty()) return;
        int idx = 0;
        for (Row r : rows.values()) {
            boolean sel = (idx == selectedRowIndex);
            r.background.setMaterial(sel ? rowMatSelected : rowMatNormal);
            try { r.remapButton.setHovered(sel); } catch (Exception ignored) {}
            idx++;
        }
    }

    private void triggerSelected() {
        if (selectedRowIndex < 0 || selectedRowIndex >= rows.size()) return;
        int idx = 0;
        for (Row r : rows.values()) {
            if (idx == selectedRowIndex) { r.remapButton.executeAction(); return; }
            idx++;
        }
    }

    // ---------- Interaction API ----------

    @Override
    public void update(float tpf) {
        for (Row r : rows.values()) {
            try { r.remapButton.update(tpf); } catch (Exception e) { LOG.warn("Button update error", e); }
        }
    }

    public void handleMouseMove(Vector2f cursor) {
        int hoverIndex = -1;
        int idx = 0;
        for (Row r : rows.values()) {
            if (r.remapButton.intersects(cursor)) { hoverIndex = idx; break; }
            idx++;
        }
        if (hoverIndex != selectedRowIndex) {
            selectedRowIndex = hoverIndex;
            highlightSelection();
        }
    }

    @Override
    public void handleMousePress(Vector2f cursor) {
        int idx = 0;
        for (Row r : rows.values()) {
            if (r.remapButton.intersects(cursor)) {
                selectedRowIndex = idx;
                highlightSelection();
                r.remapButton.executeAction();
                return;
            }
            idx++;
        }
    }

    @Override
    public void handleMouseDrag(Vector2f cursor) { /* nop */ }

    @Override
    public void handleMouseRelease() { /* nop */ }

    /**
     * intersects adjusted for center-origin component.
     * worldPos is the component origin in world coords (we treat it as center).
     */
    @Override
    public boolean intersects(Vector2f cursor) {
        Vector2f worldPos = new Vector2f(getWorldTranslation().x, getWorldTranslation().y);
        float halfH = contentHeight * 0.5f;
        float topY = worldPos.y + halfH;
        float bottomY = worldPos.y - halfH;
        return cursor.x >= worldPos.x && cursor.x <= worldPos.x + contentWidth &&
                cursor.y <= topY && cursor.y >= bottomY;
    }

    @Override
    public void setActive(boolean active) {
        if (!active) stopRemapListening();
        enableNav(active);
    }

    @Override
    public void setHovered(boolean hovered) { /* n/a */ }

    // ---------- Cleanup ----------

    public void cleanup() {
        setActive(false);
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

    private void safeDetach(Spatial s) {
        if (s != null && s.getParent() != null) {
            s.getParent().detachChild(s);
        }
    }

    public String getValueName(String bindId) {
        if (bindId == null) return null;
        Row r = rows.get(bindId);
        if (r == null) return null;
        try {
            return getCurrentKeyName(r.bind);
        } catch (Throwable t) {
            LOG.warn("getValueName(bindId) failed for {}", bindId, t);
            return null;
        }
    }
}
