package org.foxesworld.cge.ue.parser;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.foxesworld.cge.ue.model.ExportEntry;
import org.foxesworld.cge.ue.model.UPackage;

import java.io.*;
import java.nio.file.Files;

/**
 * Пример простого парсера Texture2D: просто извлекает bulk data (если есть) в файл .texraw
 * (реальная декодировка DDS/PNG требует анализа формата пикселей — оставляем как extension point).
 */
public class Texture2DParser implements TypeParser {

    @Override
    public void parse(UPackage pkg, ExportEntry export, File uexp, File outDir) throws Exception {
        String name = pkg.lookupName(export.objectName.index);
        File target = new File(outDir, sanitize(name) + ".raw");
        if (uexp == null || !uexp.exists()) {
            // попытка считать данные из того же uasset-а (если serialOffset указывает внутрь .uasset)
            throw new IllegalStateException("No uexp available for Texture2D: " + name);
        }
        try (RandomAccessFile raf = new RandomAccessFile(uexp, "r")) {
            raf.seek(export.serialOffset);
            byte[] buf = new byte[(int) Math.min(export.serialSize, Integer.MAX_VALUE)];
            raf.readFully(buf);
            Files.createDirectories(target.getParentFile().toPath());
            try (FileOutputStream fos = new FileOutputStream(target)) {
                fos.write(buf);
            }
        }
        // Дополнительно: можно сохранить мета в JSON
        Gson g = new GsonBuilder().setPrettyPrinting().create();
        try (Writer w = new FileWriter(new File(outDir, sanitize(name) + ".meta.json"))) {
            g.toJson(export, w);
        }
    }

    @Override
    public String typeName() {
        return "Texture2D";
    }

    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-\\.]", "_");
    }
}