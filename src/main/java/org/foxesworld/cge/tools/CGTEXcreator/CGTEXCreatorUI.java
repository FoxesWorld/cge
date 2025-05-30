package org.foxesworld.cge.tools.CGTEXcreator;

import com.formdev.flatlaf.FlatDarkLaf;
import org.foxesworld.cge.ICOParser;
import org.foxesworld.cge.core.file.cgtex.CGTEXFile;
import org.foxesworld.cge.core.file.cgtex.TextureEntry;
import org.foxesworld.cge.tools.CGTEXcreator.info.TextureInfo;
import org.foxesworld.cge.tools.CGTEXcreator.preview.DDSParser;
import org.foxesworld.cge.tools.CGTEXcreator.preview.PreviewCell;
import org.foxesworld.cge.tools.CGTEXcreator.preview.TextureCellRenderer;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
public class CGTEXCreatorUI extends JFrame {
    private final List<TextureInfo> textures = new ArrayList<>();
    private final DefaultListModel<TextureInfo> listModel = new DefaultListModel<>();
    private final JList<TextureInfo> fileList = new JList<TextureInfo>(listModel);
    private final JPanel previewPanel = new JPanel(new BorderLayout());
    private File selectedFile;

    public CGTEXCreatorUI() {
        super("CGTEX Creator");
        System.setProperty("log.dir", System.getProperty("user.dir"));
        System.setProperty("log.level", "DEBUG");
        try (InputStream icoStream = CGTEXCreatorUI.class.getClassLoader().getResourceAsStream("theme/icon/textureEditor.ico")) {
            if (icoStream != null) {
                ICOParser parser = new ICOParser();
                List<BufferedImage> iconsList = parser.parse(icoStream);
                BufferedImage bestIcon = parser.getBestIcon(iconsList);
                if (bestIcon != null) {
                    setIconImages(List.of(bestIcon));
                }
            } else {
                System.err.println("ICO icon resource not found");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);
        initUI();
    }

    private void initUI() {
        // --- File list panel ---
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileList.setCellRenderer(new TextureCellRenderer());
        JScrollPane listScroll = new JScrollPane(fileList);
        listScroll.setBorder(new TitledBorder("DDS Files"));

        fileList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                TextureInfo selectedFile = fileList.getSelectedValue();
                if (selectedFile != null) {
                    updatePreview(selectedFile);
                }
            }
        });

        JButton addBtn = createIconButton("Add DDS", "add_icon.png");
        JButton remBtn = createIconButton("Remove", "remove_icon.png");
        addBtn.addActionListener(e -> onAdd());
        remBtn.addActionListener(e -> onRemove());
        JPanel btns = new JPanel();
        btns.add(addBtn);
        btns.add(remBtn);

        JPanel left = new JPanel(new BorderLayout(5, 5));
        left.add(listScroll, BorderLayout.CENTER);
        left.add(btns, BorderLayout.SOUTH);

        JButton readBtn = createIconButton("Read CGTEX", "read_icon.png");
        readBtn.addActionListener(e -> onReadCGTEX());
        JPanel topBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        topBtns.add(readBtn);

        // --- Preview panel ---
        previewPanel.setBorder(new TitledBorder("Preview"));
        JScrollPane previewScroll = new JScrollPane(previewPanel);

        // --- Save button ---
        JButton save = createIconButton("Save .cgtex", "save_icon.png");
        save.addActionListener(e -> onSave());
        JPanel south = new JPanel();
        south.add(save);

        // --- Layout ---
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, previewScroll);
        split.setResizeWeight(0.3);

        getContentPane().setLayout(new BorderLayout(5, 5));
        getContentPane().add(split, BorderLayout.CENTER);
        getContentPane().add(south, BorderLayout.SOUTH);
        getContentPane().add(topBtns, BorderLayout.NORTH);

        // Инициализация списка файлов при старте
        initializeFileList(new ArrayList<>());
    }

    private void initializeFileList(List<TextureInfo> fileNames) {
        listModel.clear();
        for (TextureInfo info : fileNames) {
            listModel.addElement(info);
        }
    }

    private JButton createIconButton(String text, String iconName) {
        ImageIcon icon = new ImageIcon(getClass().getClassLoader().getResource("Textures/icons/" + iconName));
        icon = new ImageIcon(icon.getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH));
        JButton button = new JButton(text);
        button.setIcon(icon);
        button.setHorizontalTextPosition(SwingConstants.RIGHT);
        button.setIconTextGap(10);
        return button;
    }

    private void updatePreview(TextureInfo textureInfo) {
        previewPanel.removeAll();
        try {
            if (textureInfo != null) {
                PreviewCell previewCell = new PreviewCell(textureInfo);
                previewPanel.add(previewCell, BorderLayout.CENTER);
            } else {
                JOptionPane.showMessageDialog(this, "Texture not found: " + textureInfo.getName(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Cannot display preview: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
        previewPanel.revalidate();
        previewPanel.repaint();
    }


    private void onReadCGTEX() {
        JFileChooser c = new JFileChooser();
        c.setFileFilter(new FileNameExtensionFilter("CGTEX", "cgtex"));
        if (c.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        selectedFile = c.getSelectedFile();
        try (CGTEXFile reader = new CGTEXFile(selectedFile, "r")) {
            List<TextureEntry> loadedTextureEntries = reader.readFile().getTextures();

            List<TextureInfo> loadedTextures = new ArrayList<>();
            for (TextureEntry entry : loadedTextureEntries) {
                TextureInfo info = convertToTextureInfo(entry);
                loadedTextures.add(info);
            }

            // Очистить список текстур перед добавлением новых
            textures.clear();
            textures.addAll(loadedTextures);
            initializeFileList(loadedTextures);

            JOptionPane.showMessageDialog(this, "Loaded CGTEX: " + selectedFile.getName(), "Success", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Cannot read CGTEX: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private TextureInfo convertToTextureInfo(TextureEntry entry) {
        return new TextureInfo(new File(entry.getName()), entry.getWidth(), entry.getHeight(), entry.getName(), entry.getFormat(), entry.getCompressedData());
    }

    private void onAdd() {
        JFileChooser c = new JFileChooser();
        c.setMultiSelectionEnabled(true);
        c.setFileFilter(new FileNameExtensionFilter("DDS", "dds"));
        if (c.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        for (File f : c.getSelectedFiles()) {
            try {
                // Чтение данных DDS-файла в байтовый массив
                byte[] fileBytes = Files.readAllBytes(f.toPath());

                // Парсинг байтовых данных для создания TextureInfo
                TextureInfo ti = DDSParser.parseBytes(fileBytes);  // Обновленный метод для работы с байтами

                // Устанавливаем имя и другие атрибуты для текстуры
                ti.setName(ti.removeExtension(f.getName()));

                // Добавляем текстуру в список
                textures.add(ti);

                // Добавляем отображение в JList (используем имя текстуры как строку)
                listModel.addElement(ti);

            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Cannot parse DDS: " + f.getName() + "\n" + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void onRemove() {
        for (TextureInfo textureInfo : fileList.getSelectedValuesList()) {
            textures.removeIf(ti -> ti.getName().equals(textureInfo.getName()));
            listModel.removeElement(textureInfo);
        }
    }

    private void onSave() {
        if (textures.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No textures", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if(this.selectedFile == null) {
            JFileChooser c = new JFileChooser();
            c.setFileFilter(new FileNameExtensionFilter("CGTEX", "cgtex"));
            if (c.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            selectedFile = c.getSelectedFile();
        }
        if (!selectedFile.getName().endsWith(".cgtex")) selectedFile = new File(selectedFile.getParent(), selectedFile.getName() + ".cgtex");
        CGTEXFile w = new CGTEXFile(selectedFile, "rw");
        List<TextureEntry> textureEntryList = new ArrayList<>();
            for (TextureInfo ti : textures) {
                textureEntryList.add(new TextureEntry(ti.getWidth(), ti.getHeight(), ti.getName(), ti.getFormatCode(), ti.getData()));
            }
            w.writeFile(textureEntryList);
            JOptionPane.showMessageDialog(this, "Saved: " + selectedFile, "OK", JOptionPane.INFORMATION_MESSAGE);
    }

    public File getSelectedFile() {
        return selectedFile;
    }

    public static void main(String[] args) {
        FlatDarkLaf.setup(new FlatDarkLaf());
        SwingUtilities.invokeLater(() -> new CGTEXCreatorUI().setVisible(true));
    }
}
