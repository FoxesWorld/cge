package org.foxesworld.cge.ue.utils;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.FileWriter;
import java.io.Writer;

/**
 * Утиль для дампа в JSON
 */
public class JsonDump {
    private static final Gson G = new GsonBuilder().setPrettyPrinting().create();

    public static void toFile(Object obj, String path) throws Exception {
        try (Writer w = new FileWriter(path)) {
            G.toJson(obj, w);
        }
    }

    public static String toString(Object obj) {
        return G.toJson(obj);
    }
}