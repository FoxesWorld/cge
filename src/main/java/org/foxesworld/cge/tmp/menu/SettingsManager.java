package org.foxesworld.cge.tmp.menu;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.foxesworld.cge.ue.Settings;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class SettingsManager {

    public SettingsManager(String path){
        SETTINGS_FILE = path;
    }

    private final String SETTINGS_FILE;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // Загружает настройки из файла (или создаёт дефолтные)
    public Settings load() {
        try (FileReader reader = new FileReader(SETTINGS_FILE)) {
            return gson.fromJson(reader, Settings.class);
        } catch (IOException e) {
            System.out.println("[LOAD SETTINGS] Ошибка чтения, создаём дефолтные: " + e.getMessage());
            return new Settings();
        }
    }

    // Сохраняет настройки в файл
    public void save(Settings settings) {
        try (FileWriter writer = new FileWriter(SETTINGS_FILE)) {
            gson.toJson(settings, writer);
            System.out.println("[SAVE SETTINGS] JSON saved to " + SETTINGS_FILE);
        } catch (IOException e) {
            System.out.println("[SAVE SETTINGS] Ошибка записи: " + e.getMessage());
        }
    }
}
