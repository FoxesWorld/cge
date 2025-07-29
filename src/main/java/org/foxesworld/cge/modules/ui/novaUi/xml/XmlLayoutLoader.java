package org.foxesworld.cge.modules.ui.novaUi.xml;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.modules.ui.novaUi.ElementRegistry;
import org.foxesworld.cge.modules.ui.novaUi.UILayoutBuilder;
import org.foxesworld.cge.modules.ui.novaUi.UILayoutLoader;
import org.foxesworld.cge.modules.ui.novaUi.UINodeDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Objects;

public class XmlLayoutLoader implements UILayoutLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(XmlLayoutLoader.class);

    private CalistaGameEngine engine;
    private final AssetManager assetManager;
    private final String configPath;
    private final ElementRegistry elementRegistry;

    public XmlLayoutLoader(CalistaGameEngine engine, String configPath) {
        this.engine = engine;
        Objects.requireNonNull(engine, "Engine cannot be null");
        this.assetManager = engine.getAssetManager();
        this.configPath = Objects.requireNonNull(configPath, "Config path cannot be null");
        this.elementRegistry = new ElementRegistry(engine);
    }

    public XmlLayoutLoader(CalistaGameEngine engine, String configPath, ElementRegistry elementRegistry) {
        Objects.requireNonNull(engine, "Engine cannot be null");
        this.assetManager = engine.getAssetManager();
        this.configPath = configPath;
        this.elementRegistry = elementRegistry;
    }

    @Override
    public ParseResult load() throws Exception {
        LOGGER.info("Loading UI layout from asset path: {}", configPath);

        UIXmlParser parser = new UIXmlParser();
        UINodeDefinition rootDefinition;

        try {
            AssetKey<Object> assetKey = new AssetKey<>(configPath);
            AssetInfo assetInfo = assetManager.locateAsset(assetKey);
            try (InputStream inputStream = assetInfo.openStream()) {
                rootDefinition = parser.parse(inputStream);
            }

        } catch (AssetNotFoundException e) {
            LOGGER.error("Could not find the UI layout file '{}'.", configPath, e);
            throw new AssetNotFoundException("UI layout not found at path: " + configPath, e);
        }

        UILayoutBuilder builder = new UILayoutBuilder(engine, elementRegistry, rootDefinition);
        return builder.build();
    }
}