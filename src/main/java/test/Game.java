package test;

import com.jme3.system.AppSettings;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.ICOParser;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import static org.foxesworld.cge.tools.SceneCGSCreator.SceneCgsCreatorFrame.setupTheme;

public class Game {
    public static void main(String args[]){
        CalistaGameEngine app = new CalistaGameEngine();
        setupTheme("assets/theme/calista.properties");

        AppSettings settings = new AppSettings(false);
        settings.setTitle("Calista Game Engine");
        settings.setSettingsDialogImage("assets/theme/logo.png");
        settings.setFrameRate(-1);
        try (InputStream icoStream = CalistaGameEngine.class.getClassLoader().getResourceAsStream("assets/theme/icon/engineLogo.ico")) {
            ICOParser icoParser = new ICOParser();
            BufferedImage bestIcon = icoParser.getBestIcon(icoParser.parse(icoStream));
            settings.setIcons(new BufferedImage[]{bestIcon});
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Failed to load .ico icon file.");
        }

        app.setSettings(settings);
        app.start();
    }
}
