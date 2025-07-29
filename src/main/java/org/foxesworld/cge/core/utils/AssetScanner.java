package org.foxesworld.cge.core.utils;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A utility to list assets within a directory by reading a manifest file.
 * This is the canonical approach for JMonkeyEngine, as the {@link AssetManager}
 * does not support direct directory listing.
 */
public final class AssetScanner {

    private static final Logger LOGGER = LogManager.getLogger(AssetScanner.class);
    private static final String INDEX_FILE_NAME = "index.list";

    private AssetScanner() {
    }

    /**
     * Scans for assets by reading an index file from the specified directory.
     *
     * @param assetManager The application's AssetManager, used to locate the index file.
     * @param rootDirectory The directory to scan, which must contain an index file.
     * @param extension The file extension to filter by (e.g., ".json").
     * @return A list of asset paths matching the filter.
     */
    public static List<String> scan(AssetManager assetManager, String rootDirectory, String extension) {
        String normalizedRoot = rootDirectory.endsWith("/") ? rootDirectory : rootDirectory + "/";
        String indexAssetPath = normalizedRoot + INDEX_FILE_NAME;

        LOGGER.debug("Attempting to scan assets via index file: {}", indexAssetPath);

        try {
            // ИСПРАВЛЕНИЕ: Используем locateAsset вместо loadAsset для "сырых" файлов.
            // locateAsset находит ресурс и возвращает AssetInfo, не пытаясь его "загрузить"
            // с помощью специального лоадера.
            AssetInfo indexInfo = assetManager.locateAsset(new AssetKey<>(indexAssetPath));
            if (indexInfo == null) {
                // Если locateAsset вернул null, значит файл не найден.
                // Генерируем исключение, чтобы попасть в блок catch.
                throw new AssetNotFoundException("Asset index file not found: " + indexAssetPath);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(indexInfo.openStream(), StandardCharsets.UTF_8))) {

                String normalizedExtension = extension.toLowerCase();

                List<String> assetPaths = reader.lines()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#")) // Ignore empty lines and comments
                        .map(line -> line.replace("\\", "/")) // Normalize path separators
                        .filter(line -> line.toLowerCase().endsWith(normalizedExtension)) // Filter by extension
                        .collect(Collectors.toList());

                LOGGER.info("Found {} asset(s) with extension '{}' in index file '{}'",
                        assetPaths.size(), extension, indexAssetPath);

                return assetPaths;

            } catch (IOException e) {
                LOGGER.error("Failed to read asset index file stream at '{}'", indexAssetPath, e);
                return Collections.emptyList();
            }

        } catch (AssetNotFoundException e) {
            // Это не ошибка, а ожидаемый случай, если индекс не существует.
            LOGGER.warn("Asset index file not found at '{}'. To enable asset scanning, create this file. Scanning will be skipped.", indexAssetPath);
            return Collections.emptyList();
        }
    }
}