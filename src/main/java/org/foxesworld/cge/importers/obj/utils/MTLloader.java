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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * MTL file loader with improved logging and safer IO handling.
 *
 * <p>Provides robust error checks, cleaner code structure,
 * and maintains stable contracts for MaterialData population.</p>
 */
public class MTLloader {

    private static final Logger logger = LoggerFactory.getLogger(MTLloader.class);

    /**
     * Loads an MTL file from the specified path and returns a map of
     * material names to associated MaterialData instances.
     *
     * @param mgr     the AssetManager to locate and open the MTL file
     * @param mtlPath the MTL file path
     * @return a map containing material names and their data
     */
    public static Map<String, MaterialData> loadMTL(AssetManager mgr, String mtlPath) {
        Map<String, MaterialData> materials = new HashMap<>();

        try (InputStream in = mgr.locateAsset(new ModelKey(mtlPath)).openStream();
             BufferedReader br = new BufferedReader(new InputStreamReader(in))) {

            MaterialData currentMatData = null;
            String line;
            String currentMatName = null;

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] tokens = line.split("\\s+");
                String cmd = tokens[0];

                switch (cmd) {
                    case "newmtl" -> {
                        currentMatName = (tokens.length > 1) ? tokens[1] : "unnamed";
                        currentMatData = new MaterialData();
                        materials.put(currentMatName, currentMatData);
                    }
                    case "Ka" -> {
                        if (currentMatData != null && tokens.length >= 4) {
                            currentMatData.setAmbient(toColor(tokens));
                        }
                    }
                    case "Kd" -> {
                        if (currentMatData != null && tokens.length >= 4) {
                            currentMatData.setDiffuse(toColor(tokens));
                        }
                    }
                    case "Ks" -> {
                        if (currentMatData != null && tokens.length >= 4) {
                            currentMatData.setSpecular(toColor(tokens));
                        }
                    }
                    case "Ns" -> {
                        if (currentMatData != null && tokens.length == 2) {
                            currentMatData.setShininess(parseF(tokens[1]));
                        }
                    }
                    case "map_Kd" -> {
                        if (currentMatData != null && tokens.length == 2) {
                            currentMatData.setDiffuseMap(tokens[1]);
                        }
                    }
                    case "map_Bump" -> {
                        if (currentMatData != null && tokens.length == 2) {
                            currentMatData.setNormalMap(tokens[1]);
                        }
                    }
                    case "uvscale" -> {
                        if (currentMatData != null && tokens.length >= 2) {
                            applyUVScale(tokens, currentMatData);
                        }
                    }
                    case "size" -> {
                        if (currentMatData != null && tokens.length >= 2) {
                            applyScale(tokens, currentMatData);
                        }
                    }
                    case "uvoffset" -> {
                        if (currentMatData != null && tokens.length >= 2) {
                            float u = parseF(tokens[1]);
                            float v = (tokens.length > 2) ? parseF(tokens[2]) : 0f;
                            currentMatData.setTextureOffset(new Vector2f(u, v));
                        }
                    }
                    case "repeat" -> {
                        if (currentMatData != null && tokens.length == 2) {
                            currentMatData.setTextureRepeat(Boolean.parseBoolean(tokens[1]));
                        }
                    }
                    case "mass" -> {
                        if (currentMatData != null && tokens.length == 2) {
                            try {
                                currentMatData.setMass(Float.parseFloat(tokens[1]));
                            } catch (NumberFormatException e) {
                                logger.warn("Invalid mass '{}'; defaulting to 1.0f", tokens[1], e);
                                currentMatData.setMass(1.0f);
                            }
                        }
                    }
                    default -> logger.trace("Ignored MTL token: {}", cmd);
                }
            }
        } catch (Exception e) {
            logger.error("Error loading MTL file '{}'", mtlPath, e);
        }
        return materials;
    }

    private static float parseF(String s) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException ex) {
            logger.trace("Invalid float '{}', returning 0.0f", s);
            return 0.0f;
        }
    }

    /**
     * Parses and applies a UV scale to the given MaterialData.
     */
    private static void applyUVScale(String[] p, MaterialData md) {
        float u = parseF(p[1]);
        float v = (p.length > 2) ? parseF(p[2]) : u;
        md.setTextureScale(new Vector2f(u, v));
    }

    /**
     * Parses and applies a size/scale vector to the given MaterialData.
     */
    private static void applyScale(String[] p, MaterialData md) {
        float sx = parseF(p[1]);
        float sy = (p.length > 2) ? parseF(p[2]) : sx;
        float sz = (p.length > 3) ? parseF(p[3]) : sx;
        md.setScale(new Vector3f(sx, sy, sz));
    }

    /**
     * Parses color tokens into a ColorRGBA.
     *
     * @param p array of tokens (e.g., "Kd r g b")
     * @return a new ColorRGBA instance with alpha set to 1.0
     */
    private static ColorRGBA toColor(String[] p) {
        return new ColorRGBA(parseF(p[1]), parseF(p[2]), parseF(p[3]), 1.0f);
    }
}