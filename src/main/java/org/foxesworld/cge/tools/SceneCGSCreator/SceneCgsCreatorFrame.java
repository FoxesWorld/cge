package org.foxesworld.cge.tools.SceneCGSCreator;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatPropertiesLaf;
import org.foxesworld.cge.ICOParser;
import org.foxesworld.cge.core.file.extensions.cgs.CGSFile;
import org.foxesworld.cge.core.file.extensions.cgs.CGSMetadata;
import org.foxesworld.cge.tools.SceneCGSCreator.util.ChunkControlsPanel;
import org.foxesworld.cge.tools.SceneCGSCreator.util.ChunkListPanel;
import org.foxesworld.cge.tools.SceneCGSCreator.util.ChunkViewport;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class SceneCgsCreatorFrame extends JFrame {
    private CGSFile cgsFile;
    private ChunkListPanel listPanel;
    private ChunkControlsPanel controlsPanel;
    private final ChunkViewport viewport = new ChunkViewport();
    public static final JTextField sceneNameField = new JTextField(20);

    public SceneCgsCreatorFrame() {
        super("CGS Scene Creator");

        try (InputStream icoStream = SceneCgsCreatorFrame.class.getClassLoader().getResourceAsStream("assets/theme/icon/engineLogo.ico")) {
            if (icoStream != null) {
                ICOParser parser = new ICOParser();
                List<BufferedImage> iconsList = parser.parse(icoStream);  // <- вызов parse(InputStream)
                BufferedImage bestIcon = parser.getBestMatchingIcon(iconsList, 128, 128);
                if (bestIcon != null) {
                    setIconImages(List.of(bestIcon));
                }
            } else {
                System.err.println("ICO icon resource not found");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // ------------------------------
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // Выбор файла сцены в начале
        File file = promptForFile(this);
        if (file == null) {
            dispose();
            return;
        }
        cgsFile = new CGSFile(file, "rw");
        cgsFile.readFile();
        System.out.println(cgsFile.getMetadata());

        // Инициализация панелей
        listPanel = new ChunkListPanel();
        listPanel.addChunkSelectionListener(idx -> {
            if (idx < 0) viewport.clear();
            else {
                // получаем spec или данные через writer и передаем в viewport...
               // String desc = writer.getChunkSpec(idx);
               // viewport.showChunk(desc != null ? desc : "No details");
            }
        });

        controlsPanel = new ChunkControlsPanel(listPanel);
        //listPanel.setWriter(writer);
        //controlsPanel.setWriter(writer);

        // Панель ввода имени сцены
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.setBorder(BorderFactory.createTitledBorder("Scene Name"));
        topPanel.add(new JLabel("Name:"));
        topPanel.add(sceneNameField);
        add(topPanel, BorderLayout.NORTH);

        // Раздел UI на список и предпросмотр
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.3);
        splitPane.setLeftComponent(listPanel);
        JPanel viewPanel = new JPanel(new BorderLayout());
        viewPanel.setBorder(BorderFactory.createTitledBorder("Chunk Preview"));
        viewPanel.add(viewport, BorderLayout.CENTER);
        splitPane.setRightComponent(viewPanel);
        add(splitPane, BorderLayout.CENTER);

        add(controlsPanel, BorderLayout.SOUTH);
        setVisible(true);
    }

    /**
     * Показывает диалог выбора файла и возвращает файл с расширением .cgs или null при отмене
     */
    private File promptForFile(Component parent) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select or Create a CGS Scene File");
        chooser.setFileFilter(new FileNameExtensionFilter("CGS Scene Files (*.cgs)", "cgs"));
        chooser.setAcceptAllFileFilterUsed(false);

        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return null; // Пользователь отменил операцию
        }

        File file = chooser.getSelectedFile();
        // --- ИЗМЕНЕНИЕ: Более надежная проверка и добавление расширения ---
        String cgsExtension = ".cgs";
        if (!file.getName().toLowerCase().endsWith(cgsExtension)) {
            file = new File(file.getParentFile(), file.getName() + cgsExtension);
        }
        return file;
    }


    public static void main(String[] args) {
        System.setProperty("log.dir", System.getProperty("user.dir"));
        System.setProperty("log.level", "DEBUG");
        setupTheme("assets/theme/calista.properties");
        SwingUtilities.invokeLater(SceneCgsCreatorFrame::new);
    }

    public static void setupTheme(String theme) {
        try {
            InputStream themeStream = SceneCgsCreatorFrame.class.getClassLoader().getResourceAsStream(theme);

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