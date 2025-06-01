package org.foxesworld.cge.ui;


import com.jme3.app.Application;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.module.EngineModule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * UIModule: обёртка для UIPanel в виде EngineModule.
 * Загружает конфигурацию UI, создаёт и регистрирует UIPanel как AppState,
 * управляет его жизненным циклом и реакцией на перезагрузку конфигурации.
 */
public class UIModule extends EngineModule<UIConfig> {
    private final CalistaGameEngine calistaGameEngine;
    private static final Logger logger = LogManager.getLogger(UIModule.class);
    private static final String CONFIG_FILE = "ui_config";
    private List<UIPanel> uiPanels = new ArrayList<>();

    public UIModule(CalistaGameEngine app) {
        super(CONFIG_FILE, UIConfig.class, app);
        this.calistaGameEngine = app;
        logger.info("UIModule создан (config = {})", CONFIG_FILE);
    }

    @Override
    protected void initModule(CalistaGameEngine app) {
        logger.info("UIModule init...");
    }

    public void addPanel(Object handler, String xmlFile){
        if(getIsLoaded().get()) {
            UIPanel uiPanel = new UIPanel(calistaGameEngine, xmlFile);
            calistaGameEngine.getStateManager().attach(uiPanel);
            uiPanel.registerEventHandler(handler);
            uiPanels.add(uiPanel);
            logger.info("Adding new panel...");
        } else {
            logger.warn("UImodule is not loaded!");
        }
    }

    @Override
    protected void updateModule(float tpf) {
        // UIPanel сам обновляется как AppState, дополнительных действий не требуется
    }

    @Override
    protected void cleanupModule(Application app) {
        logger.info("Cleaning UIModule: disabling UIPanels...");
        if (uiPanels.size() != 0) {
            for(UIPanel uiPanel: uiPanels) {
                app.getStateManager().detach(uiPanel);
                logger.info("UIPanel {} detached.", uiPanel.getId());
            }
        }
    }

    @Override
    protected void onEnable() {
        if (uiPanels != null) {
            for(UIPanel panel: uiPanels) {
                panel.setEnabled(true);
                logger.debug("UIModule включён: UIPanel enabled.");
            }
        }
    }

    @Override
    protected void onDisable() {
        if (uiPanels.size() != 0) {
            for(UIPanel panel: uiPanels) {
                panel.setEnabled(false);
                logger.debug("UIModule отключён: UIPanel disabled.");
            }
        }
    }

    @Override
    protected void onConfigReloaded() {
        /*
        logger.info("Конфигурация UI перезагружена, пересоздаём UIPanel...");
        // 1) Отключаем старую панель
        if (uiPanels.size() != 0) {
            this.getApplication().getStateManager().detach(uiPanel);
        }
        // 2) Создаём новую тулзы из обновлённой конфигурации
        String xmlPath = getConfig().getUiXmlPath();
        uiPanel = new UIPanel(calistaGameEngine, xmlPath);

        // При необходимости снова биндим eventHandler
        Object handler = getConfig().getEventHandlerTarget();
        if (handler != null) {
            uiPanel.registerEventHandler(handler);
        }

        // 3) Регистрируем в StateManager
        this.getApplication().getStateManager().attach(uiPanel);
        logger.info("Новая UIPanel ({}) создана и прикреплена.", xmlPath);
         */
    }
}
