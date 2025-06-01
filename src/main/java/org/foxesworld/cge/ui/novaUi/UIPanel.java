package org.foxesworld.cge.ui.novaUi;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.scene.Node;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.ui.novaUi.UIXmlParser.ParseResult;
import org.foxesworld.cge.ui.novaUi.elements.panel.PanelElement;
import org.foxesworld.cge.ui.novaUi.elements.UIElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * UIPanel – «обёртка» AppState, которая:
 *  • вызывает XML-парсер при инициализации и reloadUI()
 *  • регистрирует единственную rootPanel в guiNode,
 *  • хранит allElements (id→UIElement),
 *  • передаёт eventHandlerTarget для ElementFactory,
 *  • инициализирует UIPanelUpdater (bind/update/overlap),
 *  • реагирует на изменения размера экрана (auto-reposition),
 *  • коалесцирует несколько setProperty в одном кадре.
 *
 * После изменений rootPanel **расширяется по ширине/высоте** под всех своих детей:
 *  – вычисляем «натуральный» размер через recomputeSizeAndRepositionChildren(),
 *  – если fixedWidth < neededWidth, или autoWidth=true, делаем setFixedWidth(neededWidth),
 *  – аналогично по высоте,
 *  – заново recomputeSizeAndRepositionChildren(), чтобы дети «прижались» к левому-нижнему углу,
 *  – ставим rootPanel.setLocalTranslation(0, 0, 0), чтобы он начинался от начала GUI-координат.
 */
public class UIPanel extends BaseAppState {

    private static final Logger LOGGER = LoggerFactory.getLogger(UIPanel.class);

    private final CalistaGameEngine engine;
    private final Node guiNode;
    private final String configPath;

    private PanelElement rootPanel;
    private Map<String, UIElement> allElements;

    // Обновления UI (bind/update/overlap)
    private final UIPanelUpdater updater = new UIPanelUpdater();

    // Coalesce dirty
    private boolean globalDirty = false;
    private PanelElement dirtyRoot = null;

    // Камера для реагирования на ресайз (если нужно)
    private int lastCamWidth  = -1;
    private int lastCamHeight = -1;

    public UIPanel(CalistaGameEngine engine, String configPath) {
        this.engine     = engine;
        this.guiNode    = engine.getGuiNode();
        this.configPath = configPath;
        LOGGER.info("UIPanel created (config = {})", configPath);
    }

    /**
     * Регистрируем обработчик кликов/полей (HUDController, MainApp и т.п.).
     */
    public void registerEventHandler(Object handler) {
        updater.setEventHandlerTarget(handler);
        LOGGER.info("Event handler registered: {}", handler.getClass().getSimpleName());
    }

    @Override
    protected void initialize(Application app) {
        // Сохраняем исходные размеры камеры (guiNode)
        lastCamWidth  = engine.getCamera().getWidth();
        lastCamHeight = engine.getCamera().getHeight();
        LOGGER.info("Initializing UIPanel: camera = {}×{}", lastCamWidth, lastCamHeight);

        try {
            // 1) Парсим XML → получаем rootPanel + allElements
            loadConfiguration();

            // 2) Прикрепляем rootPanel к guiNode и сразу ставим на (0,0,0)
            guiNode.attachChild(rootPanel.getNode());
            rootPanel.getNode().setLocalTranslation(0f, 0f, 0f);

            // 3) Передаём allElements в updater
            updater.setAllElements(allElements);

            // 4) Первичный bind + пересчёт/расширение под детей + позиционирование
            updater.bindAllFields();
            expandAndPositionRootPanel();

            LOGGER.info("UIPanel successfully initialized and attached to guiNode.");
        } catch (Exception e) {
            LOGGER.error("UIPanel initialization failed: ", e);
            throw new RuntimeException("Cannot initialize UIPanel", e);
        }
    }

    @Override
    protected void cleanup(Application app) {
        if (rootPanel != null) {
            guiNode.detachChild(rootPanel.getNode());
        }
        LOGGER.info("UIPanel cleaned up (detached rootPanel).");
        rootPanel = null;
        allElements = null;
    }

    @Override
    public void update(float tpf) {
        // 1) Пропускаем через UIPanelUpdater (bind/update/overlap)
        updater.update(tpf);

        // 2) Если в этом кадре вызывали setProperty(...) → пересчитаем once
        if (globalDirty && dirtyRoot != null) {
            dirtyRoot.recalcAndRepositionSelfAndAncestors();
            dirtyRoot = null;
            globalDirty = false;
        }

        // 3) (Опционально) авто-реакция на изменение размера экрана
        int camW = engine.getCamera().getWidth();
        int camH = engine.getCamera().getHeight();
        if (camW != lastCamWidth || camH != lastCamHeight) {
            LOGGER.debug("Camera size changed: {}×{} → {}×{}", lastCamWidth, lastCamHeight, camW, camH);
            expandAndPositionRootPanel();
            lastCamWidth  = camW;
            lastCamHeight = camH;
        }
    }

    @Override
    protected void onEnable() {
        LOGGER.debug("UIPanel enabled.");
    }

    @Override
    protected void onDisable() {
        LOGGER.debug("UIPanel disabled.");
    }

    /**
     * Меняем свойство элемента (color/pos/padding и т.п.).
     * Помечаем rootPanel «грязной» для отложенного пересчёта.
     */
    public void setProperty(String elementId, String propKey, String propValue) {
        UIElement e = allElements.get(elementId);
        if (e == null) {
            LOGGER.warn("setProperty: element '{}' not found", elementId);
            return;
        }
        e.setProperty(propKey, propValue);

        // Помечаем «верхнеуровневую» rootPanel как dirty
        PanelElement parent = e.getParentPanel();
        if (parent != null) {
            dirtyRoot = getTopmostRoot(parent);
            globalDirty = true;
        }
    }

    /** Взять «верхнеуровневую» панель (parentPanel == null) */
    private PanelElement getTopmostRoot(PanelElement panel) {
        PanelElement current = panel;
        while (current.getParentPanel() != null) {
            current = current.getParentPanel();
        }
        return current;
    }

    /** Достаёт элемент по его xml-id */
    public UIElement getElement(String id) {
        return allElements.get(id);
    }

    /** Парсит XML и обновляет rootPanel + allElements */
    private void loadConfiguration() throws Exception {
        UIXmlParser parser = new UIXmlParser(engine, configPath);
        ParseResult result = parser.parse();
        rootPanel   = result.rootPanel;
        allElements = result.allElements;
    }

    /**
     * 1) Сначала вызываем recomputeSizeAndRepositionChildren() → получаем «натуральный» размер,
     *    при котором rootPanel растянется под детей (autoWidth/autoHeight или фиксированные).
     * 2) Берём текущую ширину/высоту: neededW = rootPanel.getCurrentWidth(),
     *    neededH = rootPanel.getCurrentHeight().
     * 3) Если autoWidth или fixedWidth < neededW → выставляем setFixedWidth(neededW).
     *    Аналогично по высоте.
     * 4) Если мы что-то «поменяли», снова вызываем recomputeSizeAndRepositionChildren(), чтобы
     *    дети «прижались» к левому нижнему углу новой ширины/высоты.
     * 5) Ставим локальную позицию rootPanel.setLocalTranslation(0, 0, 0), чтобы он начинался
     *    от (0,0) GUI-узла (никакой align здесь не нужен, «в начале» — это (0,0)).
     * 6) В конце вызываем updater.fixOverlaps(rootPanel), чтобы устранить перекрытия.
     */
    /**
     * 1) Сначала вызываем recomputeSizeAndRepositionChildren() → «натуральный» размер под всех детей.
     * 2) Берём neededW = rootPanel.getCurrentWidth(), neededH = rootPanel.getCurrentHeight().
     * 3) Если autoWidth или fixedWidth < neededW → setFixedWidth(neededW). Аналогично по высоте.
     * 4) Если что-то изменилось, снова recomputeSizeAndRepositionChildren() (чтобы фон/дети стали по новым размерам).
     * 5) **Вместо setLocalTranslation(0,0,0)** теперь вызываем repositionRecursively(cameraW, cameraH),
     *    чтобы применился align + margin.
     * 6) В конце — updater.fixOverlaps(rootPanel).
     */
    private void expandAndPositionRootPanel() {
        if (rootPanel == null) {
            return;
        }

        // Шаг 1: «натуральный» размер
        rootPanel.recomputeSizeAndRepositionChildren();

        // Шаг 2: считываем эти размеры
        float neededW = rootPanel.getCurrentWidth();
        float neededH = rootPanel.getCurrentHeight();

        boolean changed = false;

        // Шаг 3: расширяем, если нужно
        if (rootPanel.isAutoWidth() || rootPanel.getFixedWidth() < neededW) {
            rootPanel.setFixedWidth(neededW);
            changed = true;
        }
        if (rootPanel.isAutoHeight() || rootPanel.getFixedHeight() < neededH) {
            rootPanel.setFixedHeight(neededH);
            changed = true;
        }

        // Шаг 4: если размеры поменяли, пересчитаем фон и детей
        if (changed) {
            rootPanel.recomputeSizeAndRepositionChildren();
        }

        // Шаг 5: теперь применяем align + margin, передав в качестве родителя размеры камеры
        int cameraW = engine.getCamera().getWidth();
        int cameraH = engine.getCamera().getHeight();
        rootPanel.repositionRecursively(cameraW, cameraH);

        // Шаг 6: fix overlaps
        updater.fixOverlaps(rootPanel);
    }


    /**
     * «Горячо» перезагружает UI — парсит XML, открепляет старый rootPanel,
     * прикрепляет новый, обновляет allElements/updater, биндим и снова expand+position.
     */
    public void reloadUI() {
        try {
            LOGGER.info("Reloading UI from {}", configPath);

            // 1) Открепляем старую панель
            if (rootPanel != null) {
                guiNode.detachChild(rootPanel.getNode());
            }

            // 2) Снова парсим XML
            loadConfiguration();

            // 3) Вешаем новую панель и сразу ставим (0,0)
            guiNode.attachChild(rootPanel.getNode());
            rootPanel.getNode().setLocalTranslation(0f, 0f, 0f);

            // 4) Обновляем allElements → updater
            updater.setAllElements(allElements);

            // 5) Новый bind + expand+position
            updater.bindAllFields();
            expandAndPositionRootPanel();

            LOGGER.info("UI successfully reloaded.");
        } catch (Exception e) {
            LOGGER.error("Failed to reload UI: ", e);
        }
    }
}
