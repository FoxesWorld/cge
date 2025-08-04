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
import com.jme3.ui.Picture;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.tmp.menu.MainMenuAppState;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * An AppState that displays a single logo with configurable animation and fade-out transition.
 */
public class LoadingAppState extends BaseAppState {

    private final LogoConfig config;
    private final AppState nextState;
    private final float startScale;
    private final float endScale;
    private final float animationDuration;
    private final boolean isSkippable;

    private Node guiNode;
    private Picture logo;
    private InputManager inputManager;

    private float elapsedTime = 0f;
    private boolean isTransitioning = false;

    // Fade-out variables
    private float fadeDuration;
    private float fadeProgress = 0f;
    private boolean isFadingOut = false;

    private static final String ACTION_SKIP = "SkipLogoAnimation";

    private final ActionListener skipListener = (name, isPressed, tpf) -> {
        if (name.equals(ACTION_SKIP) && isPressed) {
            startFadeOut();
        }
    };

    /**
     * Creates a loading/logo screen state from a configuration object.
     *
     * @param config    The configuration object containing all display and animation parameters.
     * @param nextState The AppState to attach after this one finishes.
     */
    public LoadingAppState(LogoConfig config, AppState nextState) {
        this.config = config;
        this.nextState = nextState;
        this.animationDuration = config.duration != null ? config.duration : 3.0f;
        this.startScale = config.startScale != null ? config.startScale : 0.1f;
        this.endScale = config.endScale != null ? config.endScale : 1.0f;
        this.isSkippable = config.skippable != null ? config.skippable : true;
        this.fadeDuration = config.fadeDuration != null ? config.fadeDuration : 1.0f;
    }

    @Override
    protected void initialize(Application app) {
        this.guiNode = ((CalistaGameEngine) app).getGuiNode();
        this.inputManager = app.getInputManager();

        logo = new Picture("Logo");
        logo.setImage(app.getAssetManager(), config.imagePath, true);

        // Enable transparency
        Material mat = logo.getMaterial();
        mat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        mat.setColor("Color", new ColorRGBA(1, 1, 1, 1));

        updateLogoTransform(this.startScale);
        guiNode.attachChild(logo);

        if (this.isSkippable) {
            inputManager.addMapping(ACTION_SKIP, new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
            inputManager.addListener(skipListener, ACTION_SKIP);
        }
    }

    @Override
    public void update(float tpf) {
        if (isTransitioning) return;

        if (isFadingOut) {
            fadeProgress += tpf;
            float alpha = 1.0f - (fadeProgress / fadeDuration);
            if (alpha < 0) alpha = 0;
            logo.getMaterial().setColor("Color", new ColorRGBA(1, 1, 1, alpha));

            // Optionally scale down during fade-out
            //float fadeScale = endScale * (alpha);
            //if (fadeScale < 0.1f) fadeScale = 0.1f;
            //updateLogoTransform(fadeScale);

            if (fadeProgress >= fadeDuration) {
                transitionToNextState();
            }
            return;
        }

        // Normal logo animation (scale up)
        elapsedTime += tpf;
        if (elapsedTime >= this.animationDuration) {
            updateLogoTransform(this.endScale);
            startFadeOut();
        } else {
            float progress = elapsedTime / this.animationDuration;
            float currentScale = startScale + (endScale - startScale) * progress;
            updateLogoTransform(currentScale);
        }
    }

    /**
     * Updates the logo's size and centers it on the screen by animating width and height.
     */
    private void updateLogoTransform(float scale) {
        float currentWidth = config.width * scale;
        float currentHeight = config.height * scale;

        logo.setWidth(currentWidth);
        logo.setHeight(currentHeight);

        float screenWidth = getApplication().getContext().getSettings().getWidth();
        float screenHeight = getApplication().getContext().getSettings().getHeight();

        logo.setPosition(
                (screenWidth - currentWidth) / 2f,
                (screenHeight - currentHeight) / 2f
        );
    }

    /**
     * Starts the fade-out transition.
     */
    private void startFadeOut() {
        if (!isFadingOut) {
            isFadingOut = true;
            fadeProgress = 0f;
            logo.getMaterial().getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        }
    }

    /**
     * Detaches this state and attaches the next one.
     */
    private void transitionToNextState() {
        if (isTransitioning) return;
        isTransitioning = true;
        getApplication().enqueue(() -> {
            getStateManager().detach(this);
            getStateManager().attach(this.nextState);
        });
    }

    @Override
    protected void cleanup(Application app) {
        if (this.isSkippable) {
            if (inputManager.hasMapping(ACTION_SKIP)) {
                inputManager.deleteMapping(ACTION_SKIP);
            }
            inputManager.removeListener(skipListener);
        }
        guiNode.detachChild(logo);
    }

    @Override
    protected void onEnable() {
        elapsedTime = 0f;
        isTransitioning = false;
        isFadingOut = false;
        fadeProgress = 0f;
        if (logo != null) {
            updateLogoTransform(this.startScale);
            logo.getMaterial().setColor("Color", new ColorRGBA(1, 1, 1, 1));
        }
    }

    @Override
    protected void onDisable() {}

    /**
     * Builds a chain of logo screens from a JSON file.
     */
    public static AppState buildLogoChainFromJson(AssetManager assetManager, String jsonPath) {
        Gson gson = new Gson();
        Type listType = new TypeToken<List<LogoConfig>>() {}.getType();
        List<LogoConfig> logoConfigs;
        AssetInfo assetInfo = assetManager.locateAsset(new AssetKey<>(jsonPath));
        try (InputStreamReader reader = new InputStreamReader(assetInfo.openStream(), StandardCharsets.UTF_8)) {
            logoConfigs = gson.fromJson(reader, listType);
            AppState nextStateInChain = new MainMenuAppState();
            for (int i = logoConfigs.size() - 1; i >= 0; i--) {
                LogoConfig config = logoConfigs.get(i);
                nextStateInChain = new LoadingAppState(config, nextStateInChain);
            }
            return nextStateInChain;
        } catch (IOException e) {
            throw new RuntimeException("Failed to build logo chain from JSON", e);
        }
    }
}
