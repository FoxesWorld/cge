package org.foxesworld.cge.tmp.menu;

import com.jme3.app.SimpleApplication;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.util.TangentBinormalGenerator;

/**
 * Управляет контентом и анимацией 3D-сцены для фона меню.
 * Является "поставщиком контента", не владеет ViewPort'ом.
 * Включает финальный фикс с клонированием модели.
 */
public class MenuBackground {

    // --- НАСТРОЙКИ СЦЕНЫ (изменяйте эти значения) ---
    private static final String MODEL_PATH = "assets/meshes/erika.j3o";

    private float angle = 0f;
    private final float rotationSpeed = 0.15f;
    private final float cameraDistance = 3.5f;
    private final float cameraHeight = 1.6f;
    private final Vector3f lookAtPoint = new Vector3f(0, 0.9f, 0);

    // --- Поля класса ---
    private final SimpleApplication app;
    private final Node sceneNode;
    private final Camera sceneCam;

    /**
     * Конструктор для создания контента 3D-фона.
     * @param app Ссылка на ваше приложение (ваш CalistaGameEngine подойдет).
     * @param camera Камера, которой этот класс будет анимировать.
     */
    public MenuBackground(SimpleApplication app, Camera camera) {
        this.app = app;
        this.sceneNode = new Node("MenuBackgroundScene");
        this.sceneCam = camera;

        app.enqueue(() -> {
            createSceneContent();
            addLights();
        });

    }

    public Node getSceneNode() {
        return sceneNode;
    }

    private void createSceneContent() {
        try {
            // --- ГЛАВНЫЙ ФИКС ---
            // Загружаем модель и СРАЗУ ЖЕ ее клонируем.
            // Теперь мы работаем с чистой, свежей копией, а не с объектом из кэша.
            Spatial sceneModel = app.getAssetManager().loadModel(MODEL_PATH).clone();

            TangentBinormalGenerator.generate(sceneModel);
            sceneModel.center();
            sceneNode.attachChild(sceneModel);

        } catch (Exception e) {
            System.err.println("Could not load model: " + MODEL_PATH);
            e.printStackTrace();

            com.jme3.scene.shape.Box fallbackBox = new com.jme3.scene.shape.Box(1, 1, 1);
            Spatial fallbackGeo = new com.jme3.scene.Geometry("FallbackBox", fallbackBox);
            Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
            mat.setColor("Color", ColorRGBA.Magenta);
            fallbackGeo.setMaterial(mat);
            sceneNode.attachChild(fallbackGeo);
        }
    }

    private void addLights() {
        if (sceneNode.getLocalLightList().size() > 0) return;
        sceneNode.addLight(new AmbientLight(ColorRGBA.White.mult(0.5f)));
        sceneNode.addLight(new DirectionalLight(new Vector3f(-0.5f, -0.8f, -0.3f).normalizeLocal(), ColorRGBA.White.mult(0.8f)));
    }

    public void update(float tpf) {
        angle += tpf * rotationSpeed;
        float x = FastMath.cos(angle) * cameraDistance;
        float z = FastMath.sin(angle) * cameraDistance;
        sceneCam.setLocation(new Vector3f(x, cameraHeight, z));
        sceneCam.lookAt(lookAtPoint, Vector3f.UNIT_Y);
    }

    public void cleanup() {
        sceneNode.getLocalLightList().clear();
        sceneNode.detachAllChildren();
        sceneNode.removeFromParent();
    }
}