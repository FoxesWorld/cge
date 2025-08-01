package org.foxesworld.cge;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.font.BitmapText;
import com.jme3.scene.Node;
import com.jme3.ui.Picture;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.tmp.menu.MainMenuAppState;

public class LoadingAppState extends BaseAppState {

    private Node guiNode;
    private Picture logo;
    private BitmapText loadingText;
    private Picture progressBarBackground;
    private Picture progressBarFill;

    private boolean loadingStarted = false;
    private float progress = 0f;

    private MainMenuAppState mainMenuAppState;

    private static final float BAR_WIDTH = 400f;
    private static final float BAR_HEIGHT = 30f;

    @Override
    protected void initialize(Application app) {
        CalistaGameEngine engine = (CalistaGameEngine) app;
        guiNode = engine.getGuiNode();
        mainMenuAppState = new MainMenuAppState();

        float screenWidth = engine.getContext().getSettings().getWidth();
        float screenHeight = engine.getContext().getSettings().getHeight();


        logo = new Picture("Logo");
        logo.setImage(engine.getAssetManager(), "assets/Textures/nova.png", true);
        logo.setWidth(300);
        logo.setHeight(150);
        logo.setPosition((engine.getContext().getSettings().getWidth() - 300) / 2f,
                (engine.getContext().getSettings().getHeight() - 150) / 2f + 100);
        guiNode.attachChild(logo);

        // Текст "Loading..."
        loadingText = new BitmapText(engine.getAssetManager().loadFont("Interface/Fonts/Default.fnt"), false);
        loadingText.setText("Loading...");
        loadingText.setSize(24);
        loadingText.setLocalTranslation((screenWidth - loadingText.getLineWidth()) / 2f,
                (screenHeight / 2f) + 50, 0);
        guiNode.attachChild(loadingText);

        // Прогресс-бар: фон
        progressBarBackground = new Picture("ProgressBarBackground");
        progressBarBackground.setImage(engine.getAssetManager(), "assets/Textures/progress-fill.png", true);
        progressBarBackground.setWidth(BAR_WIDTH);
        progressBarBackground.setHeight(BAR_HEIGHT);
        progressBarBackground.setPosition((screenWidth - BAR_WIDTH) / 2f, screenHeight / 2f - 20);
        progressBarBackground.getMaterial().setColor("Color", new com.jme3.math.ColorRGBA(0.2f, 0.2f, 0.2f, 1f));
        guiNode.attachChild(progressBarBackground);

        // Прогресс-бар: заливка
        progressBarFill = new Picture("ProgressBarFill");
        progressBarFill.setImage(engine.getAssetManager(), "assets/Textures/progress-fill.png", true);
        progressBarFill.setHeight(BAR_HEIGHT);
        progressBarFill.setWidth(0); // начально пуст
        progressBarFill.setPosition((screenWidth - BAR_WIDTH) / 2f, screenHeight / 2f - 20);
        progressBarFill.getMaterial().setColor("Color", new com.jme3.math.ColorRGBA(0.6f, 0.8f, 1f, 1f));
        guiNode.attachChild(progressBarFill);
    }

    @Override
    public void update(float tpf) {
        if (!loadingStarted) {
            loadingStarted = true;

            // Симуляция загрузки (в реальности здесь могут быть загрузки ресурсов, сцен и т.п.)
            getApplication().enqueue(() -> {
                new Thread(() -> {
                    try {
                        for (int i = 1; i <= 100; i++) {
                            Thread.sleep(20); // Имитация работы
                            final float p = i / 100f;
                            getApplication().enqueue(() -> updateProgress(p));
                        }

                        // Переход к меню
                        getApplication().enqueue(() -> {
                            getStateManager().detach(this);
                            getStateManager().attach(mainMenuAppState);
                        });

                    } catch (InterruptedException ignored) {
                    }
                }).start();
            });
        }
    }

    private void updateProgress(float value) {
        this.progress = value;

        float screenWidth = getApplication().getContext().getSettings().getWidth();
        float fillWidth = BAR_WIDTH * value;

        progressBarFill.setWidth(fillWidth);
        progressBarFill.setPosition((screenWidth - BAR_WIDTH) / 2f, progressBarFill.getLocalTranslation().y);
    }

    @Override
    protected void cleanup(Application app) {
        guiNode.detachChild(loadingText);
        guiNode.detachChild(logo);
        guiNode.detachChild(progressBarBackground);
        guiNode.detachChild(progressBarFill);
    }

    @Override
    protected void onEnable() {}

    @Override
    protected void onDisable() {}
}
