package org.foxesworld.cge.tmp.menu;

import com.jme3.app.SimpleApplication;
import com.jme3.bounding.BoundingVolume;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.BloomFilter;
import com.jme3.post.filters.FXAAFilter;
import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.SceneGraphVisitorAdapter;
import com.jme3.scene.Spatial;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import com.jme3.shadow.EdgeFilteringMode;
import com.jme3.util.SkyFactory;
import com.jme3.util.TangentBinormalGenerator;
import org.foxesworld.cge.importers.obj.utils.MeshUtils;

/**
 * Управляет контентом и анимацией 3D-сцены для фона меню. (Улучшенная версия)
 * Включает продвинутое освещение, тени, скайбокс и эффекты постобработки.
 */
public class MenuBackground {

    // --- НАСТРОЙКИ СЦЕНЫ (изменяйте эти значения) ---
    private static final String MODEL_PATH = "assets/meshes/house/objHouse.obj";
    // ПРИМЕЧАНИЕ: Убедитесь, что у вас есть текстура для скайбокса.
    // Можно взять стандартную из jMonkeyEngine SDK или скачать свою.
    private static final String SKYBOX_TEXTURE_PATH = "assets/Textures/sky.dds";

    // Анимация
    private float angle = 0f;
    private final float rotationSpeed = 0.15f; // Скорость вращения камеры
    private final float cameraDistance = 3.5f;   // Дистанция камеры от центра
    private final float cameraBaseHeight = 1.6f; // Базовая высота камеры
    private final float cameraBobbingAmount = 0.03f; // Амплитуда "дыхания" камеры
    private final float cameraBobbingSpeed = 0.5f; // Скорость "дыхания"
    private final Vector3f lookAtPoint = new Vector3f(0, 0.9f, 0); // Точка, куда смотрит камера

    // Качество графики
    private static final int SHADOW_MAP_SIZE = 1024; // Размер карты теней (влияет на качество теней и производительность)
    private final BloomFilter.GlowMode bloomGlowMode = BloomFilter.GlowMode.Objects; // Режим свечения
    private final float bloomIntensity = 1.5f; // Интенсивность свечения

    // --- Поля класса ---
    private final SimpleApplication app;
    private final Node sceneNode;
    private final Camera sceneCam;
    private final ViewPort viewPort; // Нам нужен ViewPort для теней и постобработки

    private DirectionalLightShadowRenderer shadowRenderer;
    private FilterPostProcessor postProcessor;
    private Spatial sky;

    /**
     * Конструктор для создания контента 3D-фона.
     * @param app Ссылка на ваше приложение.
     */
    public MenuBackground(SimpleApplication app) {
        this.app = app;
        this.sceneNode = new Node("MenuBackgroundScene");
        this.sceneCam = app.getCamera();
        this.viewPort = app.getViewPort();

        // Запускаем инициализацию в потоке OpenGL
        app.enqueue(() -> {
            setupLighting();
            setupModel();
            setupSky();
            setupPostProcessing();
        });
    }

    public Node getSceneNode() {
        return sceneNode;
    }

    private void setupModel() {
        // Вызываем наш новый, универсальный метод
        Spatial sceneModel = loadAndConfigureModel(MODEL_PATH, 0.25f);

        if (sceneModel != null) {
            // Если модель успешно загружена, прикрепляем ее к сцене
            sceneNode.attachChild(sceneModel);
        } else {
            // Если загрузчик вернул null (из-за ошибки), создаем запасной объект
            System.err.println("Model loading failed. Creating fallback box.");
            createFallbackBox();
        }
    }

    /**
     * Гибкий и переиспользуемый метод для загрузки и полной настройки модели.
     * Не привязан к конкретной сцене или пути.
     *
     * @param modelPath Путь к файлу модели в assets.
     * @param scale     Масштаб, который нужно применить к модели.
     * @return Готовый к использованию Spatial или null в случае ошибки.
     */
    public Spatial loadAndConfigureModel(String modelPath, float scale) {
        try {
            // 1. Загружаем и клонируем модель (клонирование - хорошая практика)
            Spatial model = app.getAssetManager().loadModel(modelPath).clone();

            // 2. Применяем базовые настройки
            model.setLocalScale(scale);
            model.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);

            // 3. Генерируем тангенты (замена TangentBinormalGenerator.generate)
            applyTangentGeneration(model);

            // 4. Центрируем модель (замена model.center())
            centerModel(model);

            return model;

        } catch (Exception e) {
            System.err.println("Could not load and configure model: " + modelPath);
            e.printStackTrace();
            return null; // Возвращаем null, чтобы вызывающий код мог обработать ошибку
        }
    }

    /**
     * Применяет генерацию тангентов ко всем геометриям внутри Spatial.
     * Это замена устаревшего TangentBinormalGenerator.
     *
     * @param spatial Модель (Node или Geometry), которую нужно обработать.
     */
    private void applyTangentGeneration(Spatial spatial) {
        // SceneGraphVisitor - это элегантный способ обойти все элементы модели
        spatial.breadthFirstTraversal(new SceneGraphVisitorAdapter() {
            @Override
            public void visit(Geometry geom) {
                // Для каждой геометрии вызываем наш улучшенный метод из MeshUtils
                try {
                    MeshUtils.computeTangentBinormal(geom.getMesh());
                } catch (Exception e) {
                    System.err.println("Failed to generate tangents for geometry: " + geom.getName());
                    // Можно проигнорировать или предпринять другие действия
                }
            }
        });
    }

    /**
     * Центрирует модель, перемещая ее так, чтобы центр ее BoundingVolume
     * оказался в точке (0,0,0). Это замена устаревшего spatial.center().
     *
     * @param spatial Модель для центрирования.
     */
    private void centerModel(Spatial spatial) {
        // Сначала нужно обновить ограничивающий объем, чтобы получить актуальные данные
        spatial.updateModelBound();
        BoundingVolume bv = spatial.getWorldBound();

        // Получаем вектор центра и перемещаем модель на противоположный вектор
        Vector3f centerOffset = new Vector3f(bv.getCenter().x,-0.8f ,0);
        spatial.move(centerOffset.negate());
    }

    private void setupLighting() {
        // --- 3-ТОЧЕЧНОЕ ОСВЕЩЕНИЕ ---

        // 1. Ключевой свет (Key Light) - основной, самый яркий, создает тени
        DirectionalLight keyLight = new DirectionalLight(new Vector3f(-0.8f, -0.6f, -0.5f).normalizeLocal());
        keyLight.setColor(ColorRGBA.White.mult(1.0f));
        sceneNode.addLight(keyLight);

        // 2. Заполняющий свет (Fill Light) - смягчает тени от ключевого света
        // Обычно это рассеянный свет (Ambient) или второй, менее яркий направленный свет
        AmbientLight fillLight = new AmbientLight(new ColorRGBA(0.4f, 0.4f, 0.4f, 1.0f));
        sceneNode.addLight(fillLight);

        // 3. Контровой свет (Rim Light) - подсвечивает силуэт модели сзади, отделяя ее от фона
        DirectionalLight rimLight = new DirectionalLight(new Vector3f(0.8f, 0.4f, 0.9f).normalizeLocal());
        rimLight.setColor(ColorRGBA.White.mult(0.3f));
        sceneNode.addLight(rimLight);

        // --- ТЕНИ ---
        shadowRenderer = new DirectionalLightShadowRenderer(app.getAssetManager(), SHADOW_MAP_SIZE, 3);
        shadowRenderer.setLight(keyLight); // Указываем, какой свет отбрасывает тень
        shadowRenderer.setEdgeFilteringMode(EdgeFilteringMode.PCFPOISSON); // Смягчение краев теней
        viewPort.addProcessor(shadowRenderer);
    }

    private void setupSky() {
        try {
            sky = SkyFactory.createSky(app.getAssetManager(), SKYBOX_TEXTURE_PATH, SkyFactory.EnvMapType.CubeMap);
            sky.setShadowMode(com.jme3.renderer.queue.RenderQueue.ShadowMode.Off);
            sceneNode.attachChild(sky);
        } catch (Exception e) {
            System.err.println("Could not load skybox: " + SKYBOX_TEXTURE_PATH);
        }
    }

    private void setupPostProcessing() {
        postProcessor = new FilterPostProcessor(app.getAssetManager());

        // Эффект свечения (Bloom)
        BloomFilter bloomFilter = new BloomFilter(bloomGlowMode);
        //bloomFilter.setIntensity(bloomIntensity);
        postProcessor.addFilter(bloomFilter);

        // Сглаживание (FXAA)
        FXAAFilter fxaaFilter = new FXAAFilter();

        postProcessor.addFilter(fxaaFilter);

        viewPort.addProcessor(postProcessor);
    }


    public void update(float tpf) {
        // Вращение камеры по кругу
        angle += tpf * rotationSpeed;
        float x = FastMath.cos(angle) * cameraDistance;
        float z = FastMath.sin(angle) * cameraDistance;

        // "Дыхание" камеры по вертикали
        float y = cameraBaseHeight + (FastMath.sin(angle * cameraBobbingSpeed) * cameraBobbingAmount);

        sceneCam.setLocation(new Vector3f(x, y, z));
        sceneCam.lookAt(lookAtPoint, Vector3f.UNIT_Y);
    }

    /**
     * Очищает все ресурсы, созданные этим классом.
     */
    public void cleanup() {
        // Удаляем процессоры из ViewPort
        if (shadowRenderer != null) {
            viewPort.removeProcessor(shadowRenderer);
            shadowRenderer = null;
        }
        if (postProcessor != null) {
            viewPort.removeProcessor(postProcessor);
            postProcessor = null;
        }

        // Очищаем ноду сцены
        if (sceneNode != null) {
            sceneNode.detachAllChildren();
            sceneNode.getLocalLightList().clear();
            sceneNode.removeFromParent();
        }
    }

    private void createFallbackBox() {
        com.jme3.scene.shape.Box fallbackBox = new com.jme3.scene.shape.Box(1, 1, 1);
        Spatial fallbackGeo = new com.jme3.scene.Geometry("FallbackBox", fallbackBox);
        Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Light/Lighting.j3md");
        mat.setColor("Diffuse", ColorRGBA.Magenta);
        mat.setBoolean("UseMaterialColors", true);
        fallbackGeo.setMaterial(mat);
        // Запасной куб тоже должен отбрасывать тень
        fallbackGeo.setShadowMode(com.jme3.renderer.queue.RenderQueue.ShadowMode.CastAndReceive);
        sceneNode.attachChild(fallbackGeo);
    }
}