package org.foxesworld.cge.modules.ui.novaUi;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.scene.Node;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.modules.ui.novaUi.elements.UIElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.panel.PanelElement;
import org.foxesworld.cge.modules.ui.novaUi.updater.DataBinder;
import org.foxesworld.cge.modules.ui.novaUi.updater.InteractionManager;
import org.foxesworld.cge.modules.ui.novaUi.xml.XmlLayoutLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class NovaUI extends BaseAppState {

    private static final Logger LOGGER = LoggerFactory.getLogger(NovaUI.class);

    private final CalistaGameEngine engine;
    private final Node uiNode;
    private final UILayoutLoader layoutLoader;
    private final Object eventHandler;

    private InteractionManager interactionManager;
    private DataBinder dataBinder;

    private PanelElement rootPanel;
    private Map<String, UIElement> allElements;

    private int lastCamWidth = -1;
    private int lastCamHeight = -1;

    private NovaUI(Builder builder) {
        this.engine = builder.engine;
        this.uiNode = new Node("NovaUI_Root_" + System.nanoTime());
        this.layoutLoader = builder.layoutLoader;
        this.eventHandler = builder.eventHandler;
    }

    @Override
    protected void initialize(Application app) {
        engine.getGuiNode().attachChild(this.uiNode);
        this.interactionManager = new InteractionManager(app, this.uiNode);

        lastCamWidth = engine.getCamera().getWidth();
        lastCamHeight = engine.getCamera().getHeight();
        loadAndApplyLayout();
    }

    private void loadAndApplyLayout() {
        try {
            uiNode.detachAllChildren();

            UILayoutLoader.ParseResult result = layoutLoader.load();
            this.rootPanel = result.rootPanel();
            this.allElements = new ConcurrentHashMap<>(result.allElements());

            uiNode.attachChild(rootPanel.getNode());

            interactionManager.setElements(allElements.values());
            this.dataBinder = new DataBinder(eventHandler, allElements.values());

            bindEventHandlersRecursive(rootPanel);
            updateLayout();

            LOGGER.info("NovaUI initialized successfully with root '{}'.", rootPanel.getId());
        } catch (Exception e) {
            LOGGER.error("Failed to initialize NovaUI layout", e);
            this.setEnabled(false);
            throw new RuntimeException("NovaUI initialization failed", e);
        }
    }

    private void bindEventHandlersRecursive(UIElement element) {
        if (element == null) return;
        element.setEventHandler(this.eventHandler);
        if (element instanceof PanelElement panel) {
            for (UIElement child : panel.getChildren()) {
                bindEventHandlersRecursive(child);
            }
        }
    }

    @Override
    protected void cleanup(Application app) {
        interactionManager.cleanup();
        uiNode.detachAllChildren();
        if (rootPanel != null) rootPanel = null;
        if (allElements != null) allElements.clear();
        engine.getGuiNode().detachChild(uiNode);
        LOGGER.info("NovaUI cleaned up.");
    }

    @Override
    public void update(float tpf) {
        if (!isEnabled() || rootPanel == null) return;

        handleResize();

        if (rootPanel.isLayoutDirty()) {
            updateLayout();
        }

        rootPanel.update(tpf);

        interactionManager.update(tpf);
        dataBinder.update();
    }

    private void updateLayout() {
        if(rootPanel == null) return;
        updateLayoutRecursive(rootPanel);
        repositionRootPanel();
    }

    private void updateLayoutRecursive(PanelElement panel) {
        for (UIElement child : panel.getChildren()) {
            if (child instanceof PanelElement childPanel && childPanel.isLayoutDirty()) {
                updateLayoutRecursive(childPanel);
            }
        }
        panel.updateLayout();
    }

    private void handleResize() {
        int camW = engine.getCamera().getWidth();
        int camH = engine.getCamera().getHeight();
        if (camW != lastCamWidth || camH != lastCamHeight) {
            if (rootPanel != null) {
                rootPanel.markLayoutDirty();
            }
            lastCamWidth = camW;
            lastCamHeight = camH;
        }
    }

    private void repositionRootPanel() {
        if(rootPanel == null) return;
        //rootPanel.reposition(engine.getCamera().getWidth(), engine.getCamera().getHeight());
    }

    public void reloadUI() { getApplication().enqueue(this::loadAndApplyLayout); }

    public void addElement(UIElement child, PanelElement parent) {
        getApplication().enqueue(() -> {
            if (allElements.containsKey(child.getId())) {
                LOGGER.warn("Element with ID '{}' already exists. Add operation aborted.", child.getId());
                return;
            }
            parent.addChild(child);
            allElements.put(child.getId(), child);
            bindEventHandlersRecursive(child);
        });
    }

    public void removeElement(UIElement element) {
        getApplication().enqueue(() -> {
            PanelElement parent = element.getParentPanel();
            if (parent != null) {
                parent.removeChild(element);
                allElements.remove(element.getId());
            }
        });
    }

    public void setProperty(String elementId, String propKey, String propValue) {
        UIElement element = allElements.get(elementId);
        if (element == null) {
            LOGGER.warn("setProperty: No UIElement found for id '{}'", elementId);
            return;
        }
        getApplication().enqueue(() -> element.setProperty(propKey, propValue));
    }

    public UIElement getElement(String id) {
        return allElements.get(id);
    }

    public PanelElement getRootPanel() { return rootPanel; }

    @Override
    protected void onEnable() {
        uiNode.setCullHint(Node.CullHint.Inherit);
    }

    @Override
    protected void onDisable() {
        uiNode.setCullHint(Node.CullHint.Always);
    }

    public static class Builder {
        private final CalistaGameEngine engine;
        private UILayoutLoader layoutLoader;
        private Object eventHandler;

        public Builder(CalistaGameEngine engine) {
            this.engine = Objects.requireNonNull(engine, "Engine cannot be null");
        }

        public Builder withLayout(String xmlPath) {
            this.layoutLoader = new XmlLayoutLoader(engine, xmlPath);
            return this;
        }

        public Builder withLayout(UILayoutLoader loader) {
            this.layoutLoader = loader;
            return this;
        }

        public Builder withEventHandler(Object handler) {
            this.eventHandler = handler;
            return this;
        }

        public NovaUI build() {
            if (layoutLoader == null) {
                throw new IllegalStateException("A layout must be provided via withLayout() before building.");
            }
            return new NovaUI(this);
        }
    }
}