package org.foxesworld.cge.tmp.menu;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.DepthOfFieldFilter;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.tmp.menu.components.ViceButton;
import org.foxesworld.cge.tmp.menu.components.ViceMenuBackground;
import org.foxesworld.cge.tmp.menu.xml.SceneXml;

import java.util.Optional;

/**
 * Manages the high-level state for the main menu.
 * This AppState coordinates the 3D background and the 2D UI by delegating
 * tasks to specialized handlers.
 */
public final class MainMenuAppState extends BaseAppState {

    private static final String MAIN_MENU_XML = "ui/main_menu.xml";

    private ViceMenuBackground background;
    private MenuScreenHandler screenHandler;

    private FilterPostProcessor fpp;
    private DepthOfFieldFilter dofFilter;

    @Override
    protected void initialize(Application app) {
        // AppState теперь создает только свои прямые зависимости.
        this.screenHandler = new MenuScreenHandler((CalistaGameEngine) app);

        // Пост-эффекты остаются здесь, так как они влияют на весь ViewPort.
        fpp = new FilterPostProcessor(app.getAssetManager());
        dofFilter = new DepthOfFieldFilter();
        dofFilter.setFocusDistance(0);
        dofFilter.setFocusRange(10);
        dofFilter.setBlurScale(1.4f);
        fpp.addFilter(dofFilter); // Добавляем фильтр, но пока не включаем
    }

    @Override
    protected void cleanup(Application application) {

    }

    @Override
    protected void onEnable() {
        SimpleApplication simpleApp = (SimpleApplication) getApplication();
        simpleApp.getFlyByCamera().setEnabled(false);
        simpleApp.getInputManager().setCursorVisible(true);
        simpleApp.getViewPort().addProcessor(fpp);

        // Инициализируем 3D фон
        setupBackground();
        // Инициализируем UI-менеджер
        screenHandler.initialize();
    }

    private void setupBackground() {
        // Фон создается один раз. Его конфигурация может быть загружена из XML.
        // Для простоты, здесь можно использовать временный XmlMenuBuilder.
        XmlMenuBuilder tempBuilder = new XmlMenuBuilder((CalistaGameEngine) getApplication(), ViceButton.Style.getViceStyle());
        MenuData menuData = tempBuilder.build(MAIN_MENU_XML);
        this.background = createBackgroundFromConfig(menuData.sceneConfig());
        ((SimpleApplication) getApplication()).getRootNode().attachChild(background.getSceneNode());
    }

    /**
     * Public API for actions to switch to the settings screen.
     * It controls the blur effect associated with this screen.
     */
    public void showSettingsScreen() {
        dofFilter.setEnabled(true);
        screenHandler.showSettings();
    }

    /**
     * Public API for actions to switch back to the main menu screen.
     */
    public void showMainMenuScreen() {
        dofFilter.setEnabled(false);
        screenHandler.showMainMenu();
    }

    @Override
    public void update(float tpf) {
        if (!isEnabled()) return;

        // Делегируем обновления соответствующим обработчикам
        if (background != null) {
            background.update(tpf);
        }
        if (screenHandler != null) {
            screenHandler.update(tpf);
        }
    }

    /**
     * Configures and constructs a {@link ViceMenuBackground} using parameters loaded from an XML file.
     * This method leverages the Builder pattern for clean and readable object creation.
     *
     * @param sceneConfig The data object containing scene parameters parsed from the {@code <scene>} tag.
     * @return A fully configured {@link ViceMenuBackground} instance.
     * @throws IllegalStateException if the scene configuration or the essential modelPath attribute is missing in the XML.
     */
    private ViceMenuBackground createBackgroundFromConfig(SceneXml sceneConfig) {
        // Fail-fast: Проверяем наличие критически важных данных. Если их нет,
        // нет смысла продолжать, и мы сразу сообщаем об ошибке.
        if (sceneConfig == null || sceneConfig.modelPath == null) {
            throw new IllegalStateException("Scene configuration or modelPath is missing in the XML file.");
        }

        // 1. Начинаем создание объекта с помощью Builder, передавая обязательный параметр.
        ViceMenuBackground.Builder builder = new ViceMenuBackground.Builder(sceneConfig.modelPath);

        // 2. Используем Optional.ofNullable() для безопасной установки необязательных параметров.
        //    Если атрибут в XML отсутствует, sceneConfig.skyboxPath будет null,
        //    и `ifPresent` просто не выполнится, оставив значение по умолчанию из Builder'а.
        Optional.ofNullable(sceneConfig.skyboxPath).ifPresent(builder::skybox);
        Optional.ofNullable(sceneConfig.modelScale).ifPresent(builder::modelScale);

        // 3. Собираем Vector3f из отдельных необязательных атрибутов.
        //    `orElse(0f)` предоставляет безопасное значение по умолчанию (0), если атрибут отсутствует.
        Vector3f offset = new Vector3f(
                Optional.ofNullable(sceneConfig.modelOffsetX).orElse(0f),
                Optional.ofNullable(sceneConfig.modelOffsetY).orElse(0f),
                Optional.ofNullable(sceneConfig.modelOffsetZ).orElse(0f)
        );
        builder.modelOffset(offset);

        // 4. Устанавливаем другие параметры тем же безопасным способом.
        Optional.ofNullable(sceneConfig.lookAtY)
                .ifPresent(y -> builder.cameraLookAt(new Vector3f(0, y, 0)));

        if (sceneConfig.cameraDistance != null && sceneConfig.cameraHeight != null) {
            // Используем стандартную скорость анимации, если она не указана в XML.
            builder.cameraAnimation(0.08f, sceneConfig.cameraDistance, sceneConfig.cameraHeight);
        }

        // 5. Завершаем создание объекта, вызывая build().
        return builder.build(getApplication());
    }

    @Override
    protected void onDisable() {
        if (background != null) background.cleanup();
        if (screenHandler != null) screenHandler.cleanup();

        SimpleApplication simpleApp = (SimpleApplication) getApplication();
        simpleApp.getViewPort().removeProcessor(fpp);
        simpleApp.getInputManager().setCursorVisible(false);
    }
}