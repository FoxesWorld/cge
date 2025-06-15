package org.foxesworld.cge.importers.obj.utils;

import com.jme3.asset.AssetManager;
import com.jme3.asset.ModelKey;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import org.foxesworld.cge.importers.obj.MaterialData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class MTLloader {
    private static final Logger logger = LoggerFactory.getLogger(MTLloader.class);

    /**
     * Загружает MTL-файл и возвращает карту материалов.
     */
    public static Map<String, MaterialData> loadMTL(AssetManager mgr, String mtlPath) {
        Map<String, MaterialData> mats = new HashMap<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(mgr.locateAsset(new ModelKey(mtlPath)).openStream()))) {

            MaterialData current = null;
            String name = null;
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] p = line.split("\\s+");
                switch (p[0]) {
                    case "newmtl" -> {
                        name = p[1];
                        current = new MaterialData();
                        mats.put(name, current);
                    }
                    case "Ka" -> {
                        if (current != null) {
                            current.setAmbient(toColor(p));
                        }
                    }
                    case "Kd" -> {
                        if (current != null) {
                            current.setDiffuse(toColor(p));
                        }
                    }
                    case "Ks" -> {
                        if (current != null) {
                            current.setSpecular(toColor(p));
                        }
                    }
                    case "Ns" -> {
                        if (current != null) {
                            current.setShininess(parseF(p[1]));
                        }
                    }
                    case "map_Kd" -> {
                        if (current != null) {
                            current.setDiffuseMap(p[1]);
                        }
                    }
                    case "map_Bump" -> {
                        if (current != null) {
                            current.setNormalMap(p[1]);
                        }
                    }
                    case "uvscale" -> {
                        if (current != null) {
                            applyUVScale(p, current);
                        }
                    }
                    case "size" -> {
                        if (current != null) {
                            applyScale(p, current);
                        }
                    }
                    case "uvoffset" -> {
                        if (current != null) {
                            float u = parseF(p[1]);
                            float v = p.length > 2 ? parseF(p[2]) : 0f;
                            current.setTextureOffset(new Vector2f(u, v));
                        }
                    }
                    case "repeat" -> {
                        if (current != null) {
                            current.setTextureRepeat(Boolean.parseBoolean(p[1]));
                        }
                    }

                    case "mass" -> {
                        if (current != null) {
                            try {
                                current.setMass(Float.parseFloat(p[1]));
                            } catch (NumberFormatException e) {
                                logger.warn("Invalid mass '{}', defaulting to 1", p[1]);
                                current.setMass(1f);
                            }
                        }
                    }
                    default -> logger.trace("Ignored MTL token: {}", p[0]);
                }
            }
        } catch (Exception e) {
            logger.error("Error loading MTL {}", mtlPath, e);
        }
        return mats;
    }

    private static float parseF(String s) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException ex) {
            return 0f;
        }
    }

    private static void applyUVScale(String[] p, MaterialData md) {
        float u = parseF(p[1]);
        float v = p.length > 2 ? parseF(p[2]) : u;
        md.setTextureScale(new Vector2f(u, v));
    }

    private static void applyScale(String[] p, MaterialData md) {
        float sx = parseF(p[1]);
        float sy = p.length > 2 ? parseF(p[2]) : sx;
        float sz = p.length > 3 ? parseF(p[3]) : sx;
        md.setScale(new Vector3f(sx, sy, sz));
    }

    private static ColorRGBA toColor(String[] p) {
        return new ColorRGBA(parseF(p[1]), parseF(p[2]), parseF(p[3]), 1f);
    }
}
