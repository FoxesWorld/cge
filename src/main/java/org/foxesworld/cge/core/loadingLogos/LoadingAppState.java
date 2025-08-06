package org.foxesworld.cge.core.loadingLogos;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.jme3.app.Application;
import com.jme3.app.state.AppState;
import com.jme3.app.state.BaseAppState;
import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import com.jme3.input.InputManager;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Node;
import com.jme3.texture.Texture2D;
import com.jme3.ui.Picture;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.tmp.menu.MainMenuAppState;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Оптимизированный AppState для отображения логотипов с адаптивным масштабированием
 * и плавными анимациями появления/исчезновения.
 */
public class LoadingAppState extends BaseAppState {
    // Константы
    private static final String ACTION_SKIP = "SkipLogoAnimation";
    private static final float MAX_SCREEN_COVERAGE = 0.8f;
    private static final float MIN_SCALE = 0.01f;

    private final LogoConfig config;
    private final AppState nextState;
    private final float startScale;
    private final float endScale;
    private final float animationDuration;
    private final boolean isSkippable;
    private final float fadeDuration;

    // Системные зависимости
    private Node guiNode;
    private Picture logo;
    private InputManager inputManager;

    // Состояние анимации
    private float aspectRatio;
    private float elapsedTime;
    private float fadeProgress;
    private boolean isTransitioning;
    private boolean isFadingOut;

    private final ActionListener skipListener = (name, isPressed, tpf) -> {
        if (isPressed && ACTION_SKIP.equals(name)) startFadeOut();
    };

    public LoadingAppState(LogoConfig config, AppState nextState) {
        this.config = config;
        this.nextState = nextState;
        this.animationDuration = config.duration != null ? config.duration : 3.0f;
        this.startScale = config.startScale != null ? Math.max(config.startScale, MIN_SCALE) : MIN_SCALE;
        this.endScale = config.endScale != null ? config.endScale : 1.0f;
        this.isSkippable = config.skippable != null ? config.skippable : true;
        this.fadeDuration = config.fadeDuration != null ? config.fadeDuration : 1.0f;
    }

    @Override
    protected void initialize(Application app) {
        CalistaGameEngine engine = (CalistaGameEngine) app;
        this.guiNode = engine.getGuiNode();
        this.inputManager = app.getInputManager();

        initializeLogo(app);
        registerInputs();
    }

    private void initializeLogo(Application app) {
        logo = new Picture("Logo");
        Texture2D texture = (Texture2D) app.getAssetManager().loadTexture(config.imagePath);

        // Рассчитываем пропорции изображения
        aspectRatio = (float) texture.getImage().getWidth() / texture.getImage().getHeight();
        logo.setTexture(app.getAssetManager(), texture, true);

        // Настройка материала с прозрачностью
        Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Gui/Gui.j3md");
        mat.setTexture("Texture", texture);
        mat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        mat.setColor("Color", ColorRGBA.White);
        logo.setMaterial(mat);

        // Первоначальное позиционирование
        updateLogoSizeAndPosition(startScale);
        guiNode.attachChild(logo);
    }

    private void registerInputs() {
        if (isSkippable) {
            inputManager.addMapping(ACTION_SKIP, new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
            inputManager.addListener(skipListener, ACTION_SKIP);
        }
    }

    @Override
    public void update(float tpf) {
        if (isTransitioning) return;

        if (isFadingOut) {
            updateFadeOut(tpf);
        } else {
            updateScaleAnimation(tpf);
        }
    }

    private void updateFadeOut(float tpf) {
        fadeProgress += tpf;
        float alpha = Math.max(1.0f - (fadeProgress / fadeDuration), 0);
        logo.getMaterial().setColor("Color", new ColorRGBA(1, 1, 1, alpha));

        if (fadeProgress >= fadeDuration) {
            transitionToNextState();
        }
    }

    private void updateScaleAnimation(float tpf) {
        elapsedTime += tpf;

        if (elapsedTime >= animationDuration) {
            updateLogoSizeAndPosition(endScale);
            startFadeOut();
            return;
        }

        float progress = elapsedTime / animationDuration;
        float currentScale = interpolateScale(progress);
        updateLogoSizeAndPosition(currentScale);
    }

    private float interpolateScale(float progress) {
        return startScale + (endScale - startScale) * progress;
    }

    private void updateLogoSizeAndPosition(float scale) {
        float screenWidth = getApplication().getContext().getSettings().getWidth();
        float screenHeight = getApplication().getContext().getSettings().getHeight();

        // Рассчитываем максимальные размеры с учетом процента покрытия экрана
        float maxWidth = screenWidth * MAX_SCREEN_COVERAGE;
        float maxHeight = screenHeight * MAX_SCREEN_COVERAGE;

        // Вычисляем размеры с сохранением пропорций
        float targetWidth = maxWidth * scale;
        float targetHeight = targetWidth / aspectRatio;

        // Корректировка при превышении максимальной высоты
        if (targetHeight > maxHeight) {
            targetHeight = maxHeight;
            targetWidth = targetHeight * aspectRatio;
        }

        // Устанавливаем размеры и позицию
        logo.setWidth(targetWidth);
        logo.setHeight(targetHeight);
        logo.setPosition(
                (screenWidth - targetWidth) * 0.5f,
                (screenHeight - targetHeight) * 0.5f
        );
    }

    private void startFadeOut() {
        if (!isFadingOut) {
            isFadingOut = true;
            fadeProgress = 0f;
        }
    }

    private void transitionToNextState() {
        if (isTransitioning) return;
        isTransitioning = true;

        getApplication().enqueue(() -> {
            getStateManager().detach(this);
            getStateManager().attach(nextState);
        });
    }

    @Override
    protected void cleanup(Application app) {
        cleanupInputs();
        if (logo != null && guiNode != null) {
            guiNode.detachChild(logo);
        }
    }

    private void cleanupInputs() {
        if (isSkippable && inputManager != null) {
            if (inputManager.hasMapping(ACTION_SKIP)) {
                inputManager.deleteMapping(ACTION_SKIP);
            }
            inputManager.removeListener(skipListener);
        }
    }

    @Override
    protected void onEnable() {
        resetState();
        if (logo != null) {
            updateLogoSizeAndPosition(startScale);
            logo.getMaterial().setColor("Color", ColorRGBA.White);
        }
    }

    private void resetState() {
        elapsedTime = 0f;
        fadeProgress = 0f;
        isTransitioning = false;
        isFadingOut = false;
    }

    @Override
    protected void onDisable() {}

    /**
     * Создает цепочку состояний загрузки из JSON-конфигурации
     */
    public static AppState buildLogoChainFromJson(AssetManager assetManager, String jsonPath) {
        try {
            AssetInfo assetInfo = assetManager.locateAsset(new AssetKey<>(jsonPath));
            Type listType = new TypeToken<List<LogoConfig>>() {}.getType();

            try (InputStreamReader reader = new InputStreamReader(assetInfo.openStream(), StandardCharsets.UTF_8)) {
                List<LogoConfig> configs = new Gson().fromJson(reader, listType);
                return createStateChain(configs);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to build logo chain: " + e.getMessage(), e);
        }
    }

    private static AppState createStateChain(List<LogoConfig> configs) {
        AppState nextState = new MainMenuAppState();
        for (int i = configs.size() - 1; i >= 0; i--) {
            nextState = new LoadingAppState(configs.get(i), nextState);
        }
        return nextState;
    }
}