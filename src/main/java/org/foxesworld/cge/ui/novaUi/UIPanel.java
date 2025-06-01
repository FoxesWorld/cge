package org.foxesworld.cge.ui.novaUi;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.renderer.Camera;
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
 *  • вызывает XML-парсер (UIXmlParser) при инициализации,
 *  • регистрирует корневую панель в guiNode,
 *  • хранит allElements (id→UIElement),
 *  • передаёт eventHandlerTarget для ElementFactory (чтобы можно было сразу setOnClickHandler),
 *  • инициализирует UIPanelUpdater (бизнес-логику bind/update/overlap).
 */
public class UIPanel extends BaseAppState {

    private static final Logger LOGGER = LoggerFactory.getLogger(UIPanel.class);

    private final CalistaGameEngine engine;
    private final Node guiNode;
    private final String configPath;

    private Camera cam;
    private final Node rootPanelNode = new Node("UIPanelRoot");

    private PanelElement rootPanel;
    private Map<String, UIElement> allElements;

    // Сюда UIPanelUpdater кладёт entry (UIElement→Field), lastKnownValues и dirtyPanels
    private final UIPanelUpdater updater = new UIPanelUpdater();

    public UIPanel(CalistaGameEngine engine, String configPath) {
        this.engine = engine;
        this.guiNode = engine.getGuiNode();
        this.configPath = configPath;
        LOGGER.info("UIPanel created (config = {})", configPath);
    }

    /**
     * Регистрируем обработчик кликов/полей (HUDController, MainApp и т.п.).
     * После разбора XML UIPanelUpdater попытается привязать текст и прогресс к полям.
     */
    public void registerEventHandler(Object handler) {
        updater.setEventHandlerTarget(handler);
        LOGGER.info("Event handler registered: {}", handler.getClass().getSimpleName());
    }

    @Override
    protected void initialize(Application app) {
        this.cam = app.getCamera();
        LOGGER.info("Initializing UIPanel: camera = {}×{}", cam.getWidth(), cam.getHeight());

        try {
            // 1) Парсим XML → получаем корень + allElements
            UIXmlParser parser = new UIXmlParser(engine, configPath);
            ParseResult result = parser.parse();
            rootPanel       = result.rootPanel;
            allElements     = result.allElements;

            // 2) Прикрепляем корневую панель к GUI
            guiNode.attachChild(rootPanel.getNode());

            // 3) Сообщаем updater’у, куда положить allElements
            updater.setAllElements(allElements);

            // 4) Запускаем первичный bind (привязка полей) + overlap-fix
            updater.bindAllFields();
            // 5) Расставляем панели/элементы на экране
            repositionAllRootPanels();

            LOGGER.info("UIPanel successfully initialized and attached to guiNode.");
        } catch (Exception e) {
            LOGGER.error("UIPanel initialization failed: ", e);
            throw new RuntimeException("Cannot initialize UIPanel", e);
        }
    }

    @Override
    protected void cleanup(Application app) {
        guiNode.detachChild(rootPanel.getNode());
        LOGGER.info("UIPanel cleaned up (detached from guiNode).");
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
     * Каждый кадр вызывается JME → делегируем UIPanelUpdater.update(tpf)
     */
    @Override
    public void update(float tpf) {
        updater.update(tpf);
    }

    /**
     * Помечаем, что свойство у элемента изменилось (например, color/pos/padding и т.п.).
     * UIPanelUpdater пометит эту панель «грязной», и к концу кадра она будет пересчитана.
     */
    public void setProperty(String elementId, String propKey, String propValue) {
        UIElement e = allElements.get(elementId);
        if (e == null) {
            LOGGER.warn("setProperty: element '{}' not found", elementId);
            return;
        }
        e.setProperty(propKey, propValue);
        updater.markDirty(e.getParentPanel());
    }

    /** Достаём элемент по его xml-id */
    public UIElement getElement(String id) {
        return allElements.get(id);
    }

    /**
     * Расставляем все верхнеуровневые панели (parentPanel==null) с учётом align/margin
     * и сразу проверяем overlap внутри каждой.
     */
    private void repositionAllRootPanels() {
        int w = cam.getWidth();
        int h = cam.getHeight();

        for (UIElement ue : allElements.values()) {
            if (ue instanceof PanelElement pe && pe.getParentPanel() == null) {
                pe.repositionRecursively(w, h);
                updater.fixOverlaps(pe);
            }
        }
    }
}
