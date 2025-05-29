package org.foxesworld.cge.tmp;

import com.jme3.app.Application;
import com.jme3.asset.AssetManager;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.streaming.StreamingManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class ModelLoader {

    private static final Logger logger = LoggerFactory.getLogger(ModelLoader.class);
    private final CalistaGameEngine app;
    private final AssetManager assetManager;
    private StreamingManager<String, Spatial> streamingManager;

    // Сигнализирует о завершении загрузки
    private final AtomicBoolean isModelLoaded = new AtomicBoolean(false);

    public ModelLoader(CalistaGameEngine app) {
        this.app = app;
        this.assetManager = app.getAssetManager();
        //this.streamingManager = new StreamingManager<>(this::loadModel, true, 4, 5000);
    }

    // Асинхронная загрузка модели
    public void loadModelAsync(String modelPath, Consumer<Spatial> onSuccess, Consumer<Throwable> onError) {
        if (isModelLoaded.get()) {
            logger.warn("Model already loaded: {}", modelPath);
            return;
        }

        // Используем StreamingManager для асинхронной загрузки
        streamingManager.streamAsync(modelPath, onSuccess, onError);
    }

    // Логика загрузки модели с использованием AssetManager
    private Spatial loadModel(String modelPath) {
        try {
            logger.debug("Loading model from: {}", modelPath);
            // Используем AssetManager для загрузки модели
            Spatial model = assetManager.loadModel(modelPath);
            logger.info("Model loaded successfully: {}", modelPath);
            return model;
        } catch (Exception e) {
            logger.error("Failed to load model from {}: {}", modelPath, e.getMessage());
            throw new RuntimeException("Failed to load model", e);
        }
    }

    // Добавление модели в сцену
    public void addModelToScene(String modelPath, Node parentNode) {
        loadModelAsync(modelPath, spatial -> {
            // Добавляем модель в сцену
            if (parentNode != null) {
                parentNode.attachChild(spatial);
                logger.info("Model {} added to the scene.", modelPath);
            }
        }, error -> {
            logger.error("Error loading model {}: {}", modelPath, error.getMessage());
        });
    }

    // Метод для добавления моделей в корневой узел сцены
    public void addModelToRoot(String modelPath) {
        addModelToScene(modelPath, app.getRootNode());
    }
}