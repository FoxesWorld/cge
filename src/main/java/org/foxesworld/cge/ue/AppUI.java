package org.foxesworld.cge.ue;

import org.foxesworld.cge.ue.model.ExportEntry;
import org.foxesworld.cge.ue.model.FName;
import org.foxesworld.cge.ue.model.UPackage;
import org.foxesworld.cge.ue.parser.Texture2DParser;
import org.foxesworld.cge.ue.parser.UAssetParser;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static test.Game.setupTheme;

/**
 * GUI-приложение для uasset-парсера.
 * Зависимости: ваш UAssetParser, Texture2DParser, модели (UPackage, ExportEntry, FName).
 *
 * Компилируется под Java 17. Запуск: java -cp <jar>:<deps> org.foxesworld.cge.ue.AppUI
 */
public class AppUI {

    private final JFrame frame;
    private final JTextField tfUasset;
    private final JTextField tfUexp;
    private final JTextField tfOut;
    private final JButton btnParse;
    private final JButton btnExtractAll;
    private final JButton btnExtractSelected;
    private final JProgressBar progressBar;
    private final JTextArea logArea;
    private final ExportTableModel exportTableModel;
    private final JTable exportTable;

    // parser instance
    private final UAssetParser parser;

    // last parsed package
    private UPackage lastPackage;
    private File lastUexp;
    private File lastOutDir;

    public AppUI() {
        parser = new UAssetParser();
        // регистрация базовых TypeParser-ов — добавьте свои парсеры тут
        parser.register(new Texture2DParser());

        frame = new JFrame("uAsset Explorer — Calista Game Engine");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(900, 640);
        frame.setMinimumSize(new Dimension(800, 520));
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(8, 8, 8, 8));
        frame.setContentPane(root);

        // Top: file selection panel
        JPanel filePanel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;

        tfUasset = new JTextField();
        tfUexp = new JTextField();
        tfOut = new JTextField();

        JButton btnChooseUasset = new JButton("Choose .uasset...");
        btnChooseUasset.addActionListener(e -> chooseFile(tfUasset, "uasset", false));

        JButton btnChooseUexp = new JButton("Choose .uexp/.ubulk...");
        btnChooseUexp.addActionListener(e -> chooseFile(tfUexp, "uexp", false));

        JButton btnChooseOut = new JButton("Choose output dir...");
        btnChooseOut.addActionListener(e -> chooseFile(tfOut, null, true));

        c.gridx = 0; c.gridy = 0; c.gridwidth = 1;
        filePanel.add(new JLabel("UAsset:"), c);
        c.gridx = 1; c.gridy = 0; c.gridwidth = 3; c.weightx = 1.0;
        filePanel.add(tfUasset, c);
        c.gridx = 4; c.gridy = 0; c.gridwidth = 1; c.weightx = 0;
        filePanel.add(btnChooseUasset, c);

        c.gridx = 0; c.gridy = 1; c.gridwidth = 1;
        filePanel.add(new JLabel("UExp/Bulk (optional):"), c);
        c.gridx = 1; c.gridy = 1; c.gridwidth = 3; c.weightx = 1.0;
        filePanel.add(tfUexp, c);
        c.gridx = 4; c.gridy = 1; c.gridwidth = 1; c.weightx = 0;
        filePanel.add(btnChooseUexp, c);

        c.gridx = 0; c.gridy = 2; c.gridwidth = 1;
        filePanel.add(new JLabel("Output Dir:"), c);
        c.gridx = 1; c.gridy = 2; c.gridwidth = 3; c.weightx = 1.0;
        filePanel.add(tfOut, c);
        c.gridx = 4; c.gridy = 2; c.gridwidth = 1; c.weightx = 0;
        filePanel.add(btnChooseOut, c);

        root.add(filePanel, BorderLayout.NORTH);

        // Center: split pane — left: table, right: logs/details
        exportTableModel = new ExportTableModel();
        exportTable = new JTable(exportTableModel);
        exportTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        exportTable.setAutoCreateRowSorter(true);
        TableRowSorter<ExportTableModel> sorter = new TableRowSorter<>(exportTableModel);
        exportTable.setRowSorter(sorter);

        JScrollPane tableScroll = new JScrollPane(exportTable);
        tableScroll.setBorder(BorderFactory.createTitledBorder("Exports"));

        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Log / Details"));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableScroll, logScroll);
        split.setResizeWeight(0.6);
        root.add(split, BorderLayout.CENTER);

        // Bottom: controls
        JPanel bottom = new JPanel(new GridBagLayout());
        btnParse = new JButton("Parse");
        btnExtractAll = new JButton("Extract All");
        btnExtractSelected = new JButton("Extract Selected");
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);

        btnParse.addActionListener(this::onParse);
        btnExtractAll.addActionListener(this::onExtractAll);
        btnExtractSelected.addActionListener(this::onExtractSelected);

        // initial states
        btnExtractAll.setEnabled(false);
        btnExtractSelected.setEnabled(false);

        c = new GridBagConstraints();
        c.insets = new Insets(4,4,4,4);
        c.gridx = 0; c.gridy = 0; c.weightx = 0;
        bottom.add(btnParse, c);
        c.gridx = 1; c.gridy = 0;
        bottom.add(btnExtractAll, c);
        c.gridx = 2; c.gridy = 0;
        bottom.add(btnExtractSelected, c);
        c.gridx = 3; c.gridy = 0; c.weightx = 1.0; c.fill = GridBagConstraints.HORIZONTAL;
        bottom.add(progressBar, c);

        root.add(bottom, BorderLayout.SOUTH);
    }

    private void chooseFile(JTextField target, String extension, boolean directory) {
        JFileChooser chooser = new JFileChooser();
        if (directory) {
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        } else {
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        }
        int ret = chooser.showOpenDialog(frame);
        if (ret == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            if (extension != null && !directory) {
                // simple extension check
                if (!f.getName().toLowerCase().endsWith("." + extension)) {
                    int confirm = JOptionPane.showConfirmDialog(frame,
                            "Selected file does not end with ." + extension + ". Continue?", "Confirm", JOptionPane.YES_NO_OPTION);
                    if (confirm != JOptionPane.YES_OPTION) return;
                }
            }
            target.setText(f.getAbsolutePath());
        }
    }

    private void onParse(ActionEvent e) {
        String uassetPath = tfUasset.getText().trim();
        if (uassetPath.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Please choose a .uasset file first.", "Missing file", JOptionPane.WARNING_MESSAGE);
            return;
        }
        File uasset = new File(uassetPath);
        if (!uasset.exists() || !uasset.isFile()) {
            JOptionPane.showMessageDialog(frame, "uasset file not found: " + uassetPath, "Missing file", JOptionPane.ERROR_MESSAGE);
            return;
        }
        File uexp = null;
        String uexpPath = tfUexp.getText().trim();
        if (!uexpPath.isEmpty()) {
            uexp = new File(uexpPath);
            if (!uexp.exists()) {
                int r = JOptionPane.showConfirmDialog(frame, "uexp file not found. Continue without it?", "Missing uexp", JOptionPane.YES_NO_OPTION);
                if (r != JOptionPane.YES_OPTION) return;
                uexp = null;
            }
        }
        String outPath = tfOut.getText().trim();
        File outDir = outPath.isEmpty() ? new File("out") : new File(outPath);
        if (!outDir.exists()) outDir.mkdirs();

        startParseTask(uasset, uexp, outDir);
    }

    private void startParseTask(File uasset, File uexp, File outDir) {
        btnParse.setEnabled(false);
        btnExtractAll.setEnabled(false);
        btnExtractSelected.setEnabled(false);
        exportTableModel.setPackage(null);
        log("Starting parse: " + uasset.getName());
        progressBar.setIndeterminate(true);
        ParserTask task = new ParserTask(uasset, uexp);
        task.execute();
        task.addPropertyChangeListener(evt -> {
            if ("state".equals(evt.getPropertyName())) {
                if (evt.getNewValue() == SwingWorker.StateValue.DONE) {
                    progressBar.setIndeterminate(false);
                    try {
                        UPackage pkg = task.get();
                        lastPackage = pkg;
                        lastUexp = uexp;
                        lastOutDir = outDir;
                        exportTableModel.setPackage(pkg);
                        log("Parse finished. Names: " + pkg.nameMap.size() + ", Exports: " + pkg.exportMap.size());
                        btnExtractAll.setEnabled(!pkg.exportMap.isEmpty());
                        btnExtractSelected.setEnabled(!pkg.exportMap.isEmpty());
                    } catch (InterruptedException | ExecutionException ex) {
                        logError("Parse failed: " + ex.getCause());
                        JOptionPane.showMessageDialog(frame, "Parse failed: " + ex.getCause(), "Error", JOptionPane.ERROR_MESSAGE);
                    } finally {
                        btnParse.setEnabled(true);
                        progressBar.setValue(0);
                    }
                }
            }
        });
    }

    private void onExtractAll(ActionEvent e) {
        if (lastPackage == null) return;
        File outDir = lastOutDir == null ? new File("out") : lastOutDir;
        ExtractTask t = new ExtractTask(lastPackage, lastUexp, outDir, true);
        t.execute();
    }

    private void onExtractSelected(ActionEvent e) {
        if (lastPackage == null) return;
        int[] rows = exportTable.getSelectedRows();
        if (rows == null || rows.length == 0) {
            JOptionPane.showMessageDialog(frame, "Select one or more exports in the table.", "No selection", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        // convert view rows -> model rows
        int[] modelRows = new int[rows.length];
        for (int i = 0; i < rows.length; i++) {
            modelRows[i] = exportTable.convertRowIndexToModel(rows[i]);
        }
        ExtractTask t = new ExtractTask(lastPackage, lastUexp, lastOutDir == null ? new File("out") : lastOutDir, false, modelRows);
        t.execute();
    }

    private void log(String s) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(s + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void logError(Object o) {
        log("[ERROR] " + (o == null ? "null" : o.toString()));
    }

    public void show() {
        frame.setVisible(true);
    }

    // --- SwingWorkers ------------------------------------------------

    private class ParserTask extends SwingWorker<UPackage, String> {
        private final File uasset;
        private final File uexp;

        public ParserTask(File uasset, File uexp) {
            this.uasset = uasset;
            this.uexp = uexp;
        }

        @Override
        protected UPackage doInBackground() throws Exception {
            publish("Invoking parser...");
            // parse may be blocking and heavy — run off EDT
            UPackage pkg = parser.parse(uasset, uexp);
            publish("Parser returned. Names: " + pkg.nameMap.size() + " Exports: " + pkg.exportMap.size());
            return pkg;
        }

        @Override
        protected void process(List<String> chunks) {
            for (String s : chunks) log(s);
        }
    }

    private class ExtractTask extends SwingWorker<Void, String> {
        private final UPackage pkg;
        private final File uexp;
        private final File outDir;
        private final boolean all;
        private final int[] indices;

        public ExtractTask(UPackage pkg, File uexp, File outDir, boolean all, int... indices) {
            this.pkg = pkg;
            this.uexp = uexp;
            this.outDir = outDir;
            this.all = all;
            this.indices = indices;
        }

        @Override
        protected Void doInBackground() throws Exception {
            publish("Starting extraction...");
            progressBar.setIndeterminate(false);
            int total = all ? pkg.exportMap.size() : indices.length;
            progressBar.setMaximum(total);
            progressBar.setValue(0);

            if (all) {
                for (int i = 0; i < pkg.exportMap.size(); i++) {
                    ExtractOne(i);
                    setProgress(i + 1);
                    progressBar.setValue(i + 1);
                }
            } else {
                for (int i = 0; i < indices.length; i++) {
                    ExtractOne(indices[i]);
                    setProgress(i + 1);
                    progressBar.setValue(i + 1);
                }
            }
            publish("Extraction finished.");
            return null;
        }

        private void ExtractOne(int modelIndex) {
            try {
                ExportEntry ex = pkg.exportMap.get(modelIndex);
                String name = safeName(pkg, ex);
                publish("Extracting: " + name);
                // single-export extraction: reuse parser.extractAll but only for this export - simplest: create temp package with single export
                // here we call parser.extractAll which extracts all; to avoid changing parser, we can call it but temporarily filter exports.
                // to keep parser untouched, we'll implement simple single-extract by invoking parser.extractAll on a cloned minimal package.
                UPackage single = new UPackage();
                single.nameMap = pkg.nameMap;
                single.importMap = pkg.importMap;
                single.exportMap = java.util.Collections.singletonList(ex);
                parser.extractAll(single, uexp, outDir);
            } catch (Throwable t) {
                publish("Failed to extract item index " + modelIndex + ": " + t.getMessage());
            }
        }

        @Override
        protected void process(List<String> chunks) {
            for (String s : chunks) log(s);
        }

        @Override
        protected void done() {
            progressBar.setValue(progressBar.getMaximum());
            log("Done extraction.");
        }
    }

    // --- Table model -------------------------------------------------

    private static class ExportTableModel extends AbstractTableModel {
        private final String[] cols = {"#", "Name", "Class", "Size", "Offset", "Flags"};
        private UPackage pkg;

        public void setPackage(UPackage pkg) {
            this.pkg = pkg;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return pkg == null ? 0 : pkg.exportMap.size();
        }

        @Override
        public int getColumnCount() {
            return cols.length;
        }

        @Override
        public String getColumnName(int column) {
            return cols[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (pkg == null) return null;
            ExportEntry e = pkg.exportMap.get(rowIndex);
            switch (columnIndex) {
                case 0: return rowIndex;
                case 1:
                    try {
                        if (e.objectName != null) {
                            return pkg.lookupName(e.objectName.index);
                        } else {
                            return "<no-name>";
                        }
                    } catch (Exception ex) { return "<err>"; }
                case 2:
                    try {
                        if (e.classIndex != null) return pkg.lookupName(e.classIndex.index);
                        else return "<unknown-class>";
                    } catch (Exception ex) { return "<err>"; }
                case 3: return e.serialSize;
                case 4: return e.serialOffset;
                case 5: return e.objectFlags;
                default: return null;
            }
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            switch (columnIndex) {
                case 0: return Integer.class;
                case 3: return Long.class;
                case 4: return Long.class;
                case 5: return Integer.class;
                default: return String.class;
            }
        }
    }

    private String safeName(UPackage pkg, ExportEntry e) {
        try {
            if (e == null) return "<null>";
            FName fn = e.objectName;
            if (fn == null) return "<no-name>";
            return pkg.lookupName(fn.index);
        } catch (Exception ex) {
            return "<err>";
        }
    }

    // --- main -------------------------------------------------------

    public static void main(String[] args) {
        System.setProperty("log.dir", System.getProperty("user.dir"));
        System.setProperty("log.level", "DEBUG");
        setupTheme("assets/Theme/calista.properties");
        // If args provided, prefill fields — optional behavior similar to original App
        SwingUtilities.invokeLater(() -> {
            AppUI app = new AppUI();
            if (args.length >= 1) app.tfUasset.setText(args[0]);
            if (args.length >= 2) app.tfUexp.setText(args[1]);
            if (args.length >= 3) app.tfOut.setText(args[2]);
            app.show();
        });
    }
}
