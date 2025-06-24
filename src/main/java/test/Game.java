package test;

import com.jme3.system.AppSettings;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.ICOParser;
import org.foxesworld.cge.modules.ModuleConfig;
import org.foxesworld.cge.modules.ecs.ECSConfig;
import org.foxesworld.cge.modules.ecs.ECSModule;
import org.foxesworld.cge.modules.physics.PhysicsModule;
import org.foxesworld.cge.modules.player.PlayerModule;
import org.foxesworld.cge.modules.renderer.RendererModule;
import org.foxesworld.cge.modules.scene.SceneModule;
import org.foxesworld.cge.modules.terrain.Terrain;
import org.foxesworld.cge.modules.ui.UIModule;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.foxesworld.cge.tools.SceneCGSCreator.SceneCgsCreatorFrame.setupTheme;

public class Game {
    public static void main(String args[]){

        List<ModuleConfig> cfg = List.of(
             new ModuleConfig(RendererModule::new, 20),
             new ModuleConfig(PhysicsModule::new, 35),
             new ModuleConfig(SceneModule::new,   10),
             new ModuleConfig(UIModule::new,        5),
             new ModuleConfig(PlayerModule::new, 40),
             new ModuleConfig(Terrain::new, 25),
                new ModuleConfig(ECSModule::new, 60)
         );

        CalistaGameEngine app = new CalistaGameEngine(cfg);
        setupTheme("assets/theme/calista.properties");

        AppSettings settings = new AppSettings(false);
        settings.setTitle("Calista Game Engine");
        settings.setSettingsDialogImage("assets/theme/logo3.png");
        settings.setFrameRate(-1);
        try (InputStream icoStream = CalistaGameEngine.class.getClassLoader().getResourceAsStream("assets/theme/icon/engineLogo.ico")) {
            ICOParser icoParser = new ICOParser();
            BufferedImage bestIcon = icoParser.getLargestIcon(icoParser.parse(icoStream));
            settings.setIcons(new BufferedImage[]{bestIcon});
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Failed to load .ico icon file.");
        }

        app.setSettings(settings);
        app.start();
    }
}
