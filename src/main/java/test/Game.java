package test;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatPropertiesLaf;
import com.jme3.system.AppSettings;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.ICOParser;
import org.foxesworld.cge.modules.ModuleConfig;
import org.foxesworld.cge.modules.ecs.ECSModule;
import org.foxesworld.cge.modules.inputManager.InputManagerModule;
import org.foxesworld.cge.modules.physics.PhysicsModule;
import org.foxesworld.cge.modules.player.PlayerModule;
import org.foxesworld.cge.modules.renderer.RendererModule;
import org.foxesworld.cge.modules.terrain.Terrain;
import org.foxesworld.cge.modules.ui.UIModule;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class Game {
    public static void main(String[] args){
        List<ModuleConfig> cfg = List.of(
             new ModuleConfig(InputManagerModule::new, 100),
             new ModuleConfig(RendererModule::new, 20),
             new ModuleConfig(PhysicsModule::new, 35),
                //new ModuleConfig(SceneModule::new,   10),
             new ModuleConfig(UIModule::new,        5),
             new ModuleConfig(PlayerModule::new, 40),
             new ModuleConfig(Terrain::new, 25),
             new ModuleConfig(ECSModule::new, 60)
         );

        CalistaGameEngine app;
        try {
            app = new CalistaGameEngine(cfg);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        setupTheme("assets/Theme/calista.properties");

        AppSettings settings = new AppSettings(false);
        settings.setTitle("Calista Game Engine");
        settings.setResizable(true);
        settings.setSettingsDialogImage("assets/Theme/logo3.png");
        settings.setRenderer(AppSettings.LWJGL_OPENGL45);

        settings.setTitle("Calista Experimental");
        settings.setFrameRate(-1);
        try (InputStream icoStream = CalistaGameEngine.class.getClassLoader().getResourceAsStream("assets/Theme/icon/engineLogo.ico")) {
            ICOParser icoParser = new ICOParser();
            BufferedImage bestIcon = icoParser.getLargestIcon(icoParser.parse(icoStream));
            settings.setIcons(new BufferedImage[]{bestIcon});
        } catch (IOException e) {
            e.printStackTrace();
        }

        app.setSettings(settings);
        app.start();
    }

    public static void setupTheme(String theme) {
        try {
            InputStream themeStream = Game.class.getClassLoader().getResourceAsStream(theme);

            if(themeStream == null) {
                throw new RuntimeException("Theme file not found in resources");
            }

            FlatPropertiesLaf laf = new FlatPropertiesLaf("Dark Theme", themeStream);
            FlatLaf.setup(laf);

        } catch(Exception ex) {
            // Fallback на стандартную темную тему
            FlatLaf.setup(new FlatDarkLaf());
            ex.printStackTrace();
        }

    }
}
