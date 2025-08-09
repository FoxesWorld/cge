package org.foxesworld.cge.ue.parser;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.ue.BinaryReader;
import org.foxesworld.cge.ue.model.ExportEntry;
import org.foxesworld.cge.ue.model.FName;
import org.foxesworld.cge.ue.model.NameEntry;
import org.foxesworld.cge.ue.model.UPackage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.util.*;

/**
 * Universal UAssetParser with strategy selection for UE4 / UE5 and heuristic fallback.
 *
 * Key features:
 *  - Strategy pattern: UE4Strategy, UE5Strategy, HeuristicStrategy (default).
 *  - Auto-detection of best strategy via HeaderProbe.
 *  - Safe reading of NameMap and ExportMap; multiple parsing attempts for exports.
 *  - Log4j2 logging + ParseProgressListener for UI integration.
 *
 * Note: Unreal formats vary strongly between engine versions and project-specific builds.
 * This parser aims to be robust and diagnostic; for 100% correctness for a specific UE version,
 * implement or tweak a dedicated Strategy for that version.
 */
public class UAssetParser {
    private static final Logger LOG = LogManager.getLogger(UAssetParser.class);

    private final Map<String, TypeParser> typeParsers = new HashMap<>();
    private final List<ParseProgressListener> listeners = new ArrayList<>();
    private final List<ParsingStrategy> strategies = new ArrayList<>();

    public UAssetParser() {
        // register builtin strategies (priority order matters)
        strategies.add(new UE4Strategy());
        strategies.add(new UE5Strategy());
        strategies.add(new HeuristicStrategy());
    }

    public void register(TypeParser tp) {
        typeParsers.put(tp.typeName(), tp);
        LOG.debug("Registered TypeParser: {}", tp.typeName());
    }

    public void addProgressListener(ParseProgressListener l) {
        synchronized (listeners) { listeners.add(l); }
    }

    public void removeProgressListener(ParseProgressListener l) {
        synchronized (listeners) { listeners.remove(l); }
    }

    private void fireInfo(String s) { LOG.info(s); synchronized (listeners) { for (var l : listeners) l.info(s); } }
    private void fireDebug(String s) { LOG.debug(s); synchronized (listeners) { for (var l : listeners) l.debug(s); } }
    private void fireError(String s, Throwable t) { LOG.error(s, t); synchronized (listeners) { for (var l : listeners) l.error(s, t); } }
    private void firePercent(int p) { synchronized (listeners) { for (var l : listeners) l.percent(p); } }

    /**
     * Public parse entry.
     */
    public UPackage parse(File uasset, File uexp) throws Exception {
        fireInfo("parse(): start - uasset='" + uasset + "' uexp='" + uexp + "'");
        try (BinaryReader br = new BinaryReader(uasset)) {
            br.setByteOrder(ByteOrder.LITTLE_ENDIAN);

            // Build a lightweight header probe
            HeaderProbe probe = HeaderProbe.build(br);
            fireDebug("Probe: tag=0x" + Integer.toHexString(probe.tag) + " fileVersion=" + probe.fileVersionUE4
                    + " legacy=" + probe.legacyFileVersion + " scanInts=" + probe.peekedInts.length);

            // select strategy
            ParsingStrategy chosen = null;
            for (ParsingStrategy s : strategies) {
                try {
                    if (s.matches(probe)) {
                        chosen = s;
                        fireInfo("Strategy selected: " + s.name());
                        fireDebug("Strategy " + s.name() + " matched probe.");
                        break;
                    } else {
                        fireDebug("Strategy " + s.name() + " did not match probe.");
                    }
                } catch (Throwable t) {
                    fireDebug("Strategy " + s.name() + " threw on matches: " + t.getMessage());
                }
            }
            if (chosen == null) {
                chosen = new HeuristicStrategy(); // final fallback
                fireInfo("No strategy matched probe; using heuristic fallback.");
            }

            // Attempt to read header using chosen strategy
            HeaderInfo header = chosen.readHeader(br, probe);
            if (!header.valid) {
                fireInfo("Header not detected by strategy " + chosen.name() + "; falling back to heuristic scans.");
                // fallback: run heuristic strategy explicitly
                chosen = new HeuristicStrategy();
                header = chosen.readHeader(br, probe);
            }

            UPackage pkg = new UPackage();
            if (header.valid) {
                pkg.fileVersion = probe.fileVersionUE4;
                pkg.headerSize = header.headerSize;
                // read names
                List<NameEntry> names = chosen.readNameMap(br, header);
                pkg.nameMap.addAll(names);
                // read exports
                List<ExportEntry> exports = chosen.readExportMap(br, header);
                pkg.exportMap.addAll(exports);
            } else {
                // as last resort, do heuristic name scan and empty export map (or heuristic exports)
                List<NameEntry> names = new HeuristicStrategy().readNameMap(br, header);
                pkg.nameMap.addAll(names);
                List<ExportEntry> exports = new HeuristicStrategy().readExportMap(br, header);
                pkg.exportMap.addAll(exports);
            }

            fireInfo("parse(): finished - Names: " + pkg.nameMap.size() + ", Exports: " + pkg.exportMap.size());
            return pkg;
        } catch (Exception ex) {
            fireError("parse() failed", ex);
            throw ex;
        }
    }

    /**
     * Extract everything (unchanged semantics, with improved logging).
     */
    public void extractAll(UPackage pkg, File uexp, File outDir) throws Exception {
        if (pkg == null) throw new IllegalArgumentException("pkg == null");
        if (outDir == null) outDir = new File("out");
        if (!outDir.exists()) outDir.mkdirs();

        fireInfo("extractAll(): exports=" + pkg.exportMap.size() + ", out=" + outDir.getAbsolutePath());
        int total = pkg.exportMap.size();
        int idx = 0;
        for (ExportEntry ee : pkg.exportMap) {
            idx++;
            try {
                String typeName = lookupClassName(pkg, ee.classIndex);
                fireInfo("[" + idx + "/" + total + "] Extract: " + safeObjectName(pkg, ee) + " type=" + typeName);
                TypeParser tp = typeParsers.get(typeName);
                if (tp != null) {
                    tp.parse(pkg, ee, uexp, outDir);
                } else {
                    // fallback raw read from uexp
                    if (uexp != null && ee.serialSize > 0) {
                        try (RandomAccessFile raf = new RandomAccessFile(uexp, "r")) {
                            raf.seek(ee.serialOffset);
                            long size = ee.serialSize;
                            if (size > Integer.MAX_VALUE) {
                                fireDebug("Bulk too large to read into memory for " + safeObjectName(pkg, ee));
                            } else {
                                byte[] buf = new byte[(int) size];
                                raf.readFully(buf);
                                File target = new File(outDir, sanitizeFileName(safeObjectName(pkg, ee)) + ".raw");
                                try (FileOutputStream fos = new FileOutputStream(target)) {
                                    fos.write(buf);
                                }
                                fireDebug("Wrote raw data: " + target.getAbsolutePath());
                            }
                        }
                    } else {
                        fireDebug("No parser and no bulk for " + safeObjectName(pkg, ee));
                    }
                }
            } catch (Throwable t) {
                fireError("Failed extracting export index " + (idx - 1) + " : " + safeObjectName(pkg, ee), t);
            }
            firePercent((int) (idx * 100L / Math.max(1, total)));
        }
        fireInfo("extractAll(): finished");
    }

    // ---------- helpers ----------
    private String lookupClassName(UPackage pkg, FName classIndex) {
        if (classIndex == null) return null;
        try { return pkg.lookupName(classIndex.index); } catch (Exception ex) { return null; }
    }
    private String safeObjectName(UPackage pkg, ExportEntry ee) {
        try {
            if (ee == null || ee.objectName == null) return "<no-name>";
            return pkg.lookupName(ee.objectName.index);
        } catch (Exception ex) { return "<err>"; }
    }
    private String sanitizeFileName(String n) {
        return n == null ? "unknown" : n.replaceAll("[^a-zA-Z0-9_\\-\\.]", "_");
    }

    // ---------------------------
    // HeaderProbe + HeaderInfo
    // ---------------------------
    private static final int UE4_TAG = 0x9E2A83C1;
    private static final int MAX_HEADER_SCAN = 4096;

    public static final class HeaderProbe {
        public final int tag;
        public final int legacyFileVersion;
        public final int fileVersionUE4;
        public final int licenseeUE4Version;
        public final int[] peekedInts; // extra ints read after initial header

        private HeaderProbe(int tag, int legacyFileVersion, int fileVersionUE4, int licenseeUE4Version, int[] peekedInts) {
            this.tag = tag;
            this.legacyFileVersion = legacyFileVersion;
            this.fileVersionUE4 = fileVersionUE4;
            this.licenseeUE4Version = licenseeUE4Version;
            this.peekedInts = peekedInts;
        }

        public static HeaderProbe build(BinaryReader br) {
            try {
                br.seek(0);
                int tag = br.readInt();
                int legacy = br.readInt();
                int fv = br.readInt();
                int lic = br.readInt();
                // read extra window of ints for heuristics
                int window = 32;
                int[] peeked = new int[window];
                for (int i = 0; i < window; i++) {
                    try { peeked[i] = br.readInt(); } catch (Exception ex) { peeked[i] = 0; }
                }
                return new HeaderProbe(tag, legacy, fv, lic, peeked);
            } catch (Exception ex) {
                // fallback if file shorter
                return new HeaderProbe(0, 0, 0, 0, new int[0]);
            }
        }
    }

    public static final class HeaderInfo {
        public boolean valid;
        public int nameCount;
        public long nameOffset;
        public int exportCount;
        public long exportOffset;
        public int importCount;
        public long importOffset;
        public long headerSize;
    }

    // ---------------------------
    // Strategy interface + builtins
    // ---------------------------
    private interface ParsingStrategy {
        boolean matches(HeaderProbe probe);
        HeaderInfo readHeader(BinaryReader br, HeaderProbe probe);
        List<NameEntry> readNameMap(BinaryReader br, HeaderInfo header);
        List<ExportEntry> readExportMap(BinaryReader br, HeaderInfo header);
        String name();
    }

    // ---------------------------
    // UE4Strategy (best-effort)
    // ---------------------------
    private class UE4Strategy implements ParsingStrategy {
        @Override
        public boolean matches(HeaderProbe probe) {
            // If tag matches and fileVersionUE4 is non-zero -> likely UE4
            if (probe.tag == UE4_TAG && probe.fileVersionUE4 != 0) return true;
            // additional heuristic: peeked ints often contain plausible nameCount at position 4..10
            for (int i = 4; i + 5 < probe.peekedInts.length; i++) {
                int nc = probe.peekedInts[i];
                int no = probe.peekedInts[i + 1];
                int ec = probe.peekedInts[i + 2];
                if (plausibleCount(nc) && UAssetParser.this.plausibleOffset(no) && plausibleCount(ec)) return true;
            }
            return false;
        }

        @Override
        public HeaderInfo readHeader(BinaryReader br, HeaderProbe probe) {
            HeaderInfo h = new HeaderInfo();
            h.valid = false;
            try {
                // 1) Найдём место тега (точно или в пределах первых MAX_HEADER_SCAN)
                int tagPos = -1;
                for (int pos = 0; pos <= MAX_HEADER_SCAN - 4; pos += 4) {
                    try {
                        br.seek(pos);
                        if (br.readInt() == UE4_TAG) { tagPos = pos; break; }
                    } catch (IOException ex) { break; }
                }
                if (tagPos < 0) {
                    fireDebug("UE4Strategy: tag not found in first " + MAX_HEADER_SCAN + " bytes");
                    return h;
                }
                fireDebug("UE4Strategy: tag found at 0x" + Integer.toHexString(tagPos));

                // 2) Сканируем более широкое окно после тега (16 KB) в поисках tuple.
                final int WINDOW = 16 * 1024;
                int scanStart = tagPos + 4;
                int scanEnd = tagPos + WINDOW;
                long fileLen = br.readInt(); // если BinaryReader умеет возвращать длину; иначе используем uasset.length() сверху
                for (int pos = scanStart; pos <= scanEnd - 24; pos += 4) {
                    try {
                        br.seek(pos);
                        int a = br.readInt(); // candidate nameCount
                        int b = br.readInt(); // candidate nameOffset
                        int c = br.readInt(); // exportCount
                        int d = br.readInt(); // exportOffset
                        int e = br.readInt(); // importCount
                        int f = br.readInt(); // importOffset

                        if (!plausibleCount(a) || !plausibleOffset(b, fileLen) || !plausibleCount(c) || !plausibleOffset(d, fileLen) || !plausibleCount(e) || !plausibleOffset(f, fileLen)) {
                            continue;
                        }

                        // Дополнительная валидация: nameOffset действительно указывает на FString-последовательность
                        if (!validateNameOffsetAt(br, Integer.toUnsignedLong(b))) continue;

                        // Положительная валидация: убедимся что exportOffset указывает на возможную экспортную структуру (быстрая проверка)
                        if (!quickValidateExportOffset(br, Integer.toUnsignedLong(d), c, fileLen)) {
                            // Можно принять, но ставим пометку; попробуем следующий кандидатов
                            fireDebug("UE4Strategy: candidate at 0x" + Integer.toHexString(pos) + " failed quickExportValidation");
                            continue;
                        }

                        // если дошли сюда — считаем, что нашли header
                        h.valid = true;
                        h.nameCount = a; h.nameOffset = Integer.toUnsignedLong(b);
                        h.exportCount = c; h.exportOffset = Integer.toUnsignedLong(d);
                        h.importCount = e; h.importOffset = Integer.toUnsignedLong(f);
                        h.headerSize = pos - tagPos;
                        fireDebug(String.format("UE4Strategy: header matched at 0x%08X nameCount=%d nameOffset=0x%08X exportCount=%d exportOffset=0x%08X",
                                pos, a, b, c, d));
                        return h;
                    } catch (IOException ex) {
                        fireDebug("UE4Strategy: read at pos 0x" + Integer.toHexString(pos) + " failed: " + ex.getMessage());
                        continue;
                    }
                }
                fireDebug("UE4Strategy: no plausible header tuple found in window");
            } catch (Throwable t) {
                fireError("UE4Strategy.readHeader failed", t);
            }
            return h;
        }

        /* Вспомогательные методы (добавь рядом в класс UE4Strategy или как private в UAssetParser) */
        private boolean plausibleOffset(int off, long fileLen) {
            if (off < 0) return false;
            if (fileLen > 0) return off < fileLen;
            return off < (1 << 30);
        }

        private boolean validateNameOffsetAt(BinaryReader br, long nameOffset) {
            try {
                if (nameOffset <= 0) return false;
                br.seek(nameOffset);
                int len = br.readInt();
                if (len == 0) return true;
                int abs = Math.abs(len);
                if (abs > 65536) return false;
                int toRead = Math.min(abs, 128);
                byte[] sample = br.readBytes(toRead);
                int printable = 0;
                for (byte b : sample) {
                    int ub = b & 0xFF;
                    if (ub == 0) continue;
                    if (ub >= 32 || ub == 9 || ub == 10 || ub == 13) printable++;
                }
                return printable > 0;
            } catch (Throwable t) {
                return false;
            }
        }

        private boolean quickValidateExportOffset(BinaryReader br, long exportOffset, int exportCount, long fileLen) {
            try {
                if (exportOffset <= 0 || exportCount <= 0) return false;
                if (fileLen > 0 && exportOffset > fileLen) return false;
                br.seek(exportOffset);
                // прочитать первые 1-2 полей первой export записи без изменения pointers
                int maybeClass = br.readInt();
                int maybeSuper = br.readInt();
                // класс/супер — должны быть разумными int-значениями (в пределах допустимого)
                return Math.abs(maybeClass) < 1_000_000 && Math.abs(maybeSuper) < 1_000_000;
            } catch (Throwable t) {
                return false;
            }
        }

        @Override
        public List<NameEntry> readNameMap(BinaryReader br, HeaderInfo header) {
            List<NameEntry> list = new ArrayList<>();
            if (!header.valid || header.nameCount <= 0) return list;
            try {
                fireDebug("UE4Strategy.readNameMap: count=" + header.nameCount + " offset=0x" + Long.toHexString(header.nameOffset));
                br.seek(header.nameOffset);
                for (int i = 0; i < header.nameCount; i++) {
                    String name = br.readFString();
                    int flags = br.readInt();
                    list.add(new NameEntry(name == null ? "<null>" : name, flags));
                    if ((i & 0x3FF) == 0) firePercent((int) (i * 100L / Math.max(1, header.nameCount)));
                }
            } catch (Throwable t) {
                fireError("UE4Strategy.readNameMap failed", t);
            }
            return list;
        }

        @Override
        public List<ExportEntry> readExportMap(BinaryReader br, HeaderInfo header) {
            List<ExportEntry> exports = new ArrayList<>();
            if (!header.valid || header.exportCount <= 0) return exports;
            fireDebug("UE4Strategy.readExportMap: trying to parse " + header.exportCount + " entries at 0x" + Long.toHexString(header.exportOffset));
            try {
                // Попробуем Variant A (широкая запись: class, super, template, outer, nameIdx, nameNum, flags, serialSize, serialOffset)
                List<ExportEntry> a = tryParseExportVariantA(br, header);
                int validA = validateExportEntries(a, header);
                fireDebug("VariantA parsed " + a.size() + " entries, valid count = " + validA);

                // Если A выглядит правдоподобно (много валидных записей) — выбираем его
                if (validA > Math.max(1, header.exportCount / 10)) { // >10% считаем достаточным
                    fireDebug("Choosing VariantA for export map");
                    return a;
                }

                // Иначе пробуем Variant B (меньший layout / иные сдвиги)
                List<ExportEntry> b = tryParseExportVariantB(br, header);
                int validB = validateExportEntries(b, header);
                fireDebug("VariantB parsed " + b.size() + " entries, valid count = " + validB);

                if (validB > validA) {
                    fireDebug("Choosing VariantB for export map");
                    return b;
                } else {
                    fireDebug("Neither variant fully convincing; returning best-effort (VariantA)");
                    return a;
                }
            } catch (Throwable t) {
                fireError("UE4Strategy.readExportMap failed", t);
                return exports;
            }
        }

        /* Variant A: typical UE4 layout */
        private List<ExportEntry> tryParseExportVariantA(BinaryReader br, HeaderInfo header) {
            List<ExportEntry> res = new ArrayList<>();
            try {
                br.seek(header.exportOffset);
                for (int i = 0; i < header.exportCount; i++) {
                    long pos = br.position();
                    int classIndexRaw = br.readInt();
                    int superIndexRaw = br.readInt();
                    int templateIndexRaw = br.readInt();
                    int outerIndexRaw = br.readInt();
                    int objectNameIndex = br.readInt();
                    int objectNameNumber = br.readInt();
                    int objectFlags = br.readInt();
                    long serialSize = br.readLong();
                    long serialOffset = br.readLong();
                    FName classIndex = new FName(Math.abs(classIndexRaw), 0);
                    FName superIndex = new FName(Math.abs(superIndexRaw), 0);
                    FName templateIndex = new FName(Math.abs(templateIndexRaw), 0);
                    FName objName = new FName(objectNameIndex, objectNameNumber);
                    res.add(new ExportEntry(classIndex, superIndex, templateIndex, objName, serialSize, serialOffset, objectFlags));
                }
            } catch (Throwable t) {
                fireDebug("tryParseExportVariantA: stopped early, reason: " + t.getMessage());
            }
            return res;
        }

        /* Variant B: альтернативный (например: fewer ints before name or 32-bit serials) */
        private List<ExportEntry> tryParseExportVariantB(BinaryReader br, HeaderInfo header) {
            List<ExportEntry> res = new ArrayList<>();
            try {
                br.seek(header.exportOffset);
                for (int i = 0; i < header.exportCount; i++) {
                    long pos = br.position();
                    // Пробуем порядок: class, outer, nameIdx, nameNum, flags, serialSize (32), serialOffset (32)
                    int classIndexRaw = br.readInt();
                    int outerIndexRaw = br.readInt();
                    int objectNameIndex = br.readInt();
                    int objectNameNumber = br.readInt();
                    int objectFlags = br.readInt();
                    int serialSize32 = br.readInt();
                    int serialOffset32 = br.readInt();
                    long serialSize = Integer.toUnsignedLong(serialSize32);
                    long serialOffset = Integer.toUnsignedLong(serialOffset32);
                    FName classIndex = new FName(Math.abs(classIndexRaw), 0);
                    FName objName = new FName(objectNameIndex, objectNameNumber);
                    res.add(new ExportEntry(classIndex, null, null, objName, serialSize, serialOffset, objectFlags));
                }
            } catch (Throwable t) {
                fireDebug("tryParseExportVariantB: stopped early, reason: " + t.getMessage());
            }
            return res;
        }

        /* Валидация: считаем запись валидной, если:
           - objectName.index в разумном диапазоне (1..nameCount)
           - serialOffset/serialSize попадают в пределах файла или нулевы
        */
        private int validateExportEntries(List<ExportEntry> entries, HeaderInfo header) {
            if (entries == null || entries.isEmpty()) return 0;

            // Попытка получить длину файла из header (если есть) через reflection (несколько возможных имён)
            long fileLen = 0;
            try {
                // наиболее вероятные имена поля в HeaderInfo
                String[] possibleNames = new String[] { "fileSize", "fileLen", "fileLength", "packageSize" };
                for (String n : possibleNames) {
                    try {
                        java.lang.reflect.Field f = header.getClass().getDeclaredField(n);
                        f.setAccessible(true);
                        Object val = f.get(header);
                        if (val instanceof Number) {
                            fileLen = ((Number) val).longValue();
                            break;
                        }
                    } catch (NoSuchFieldException ignored) {
                        // пробуем следующее имя
                    }
                }
            } catch (Throwable t) {
                LOG.debug("validateExportEntries: cannot read fileLen from header via reflection: {}", t.toString());
                fileLen = 0;
            }

            int validCount = 0;
            int idxCounter = 0;
            for (ExportEntry ee : entries) {
                idxCounter++;
                if (ee == null) continue;

                int score = 0;

                // 1) Проверка objectName.index
                try {
                    if (ee.objectName != null) {
                        int nameIdx = ee.objectName.index;
                        if (header != null && header.nameCount > 0) {
                            if (nameIdx > 0 && nameIdx <= header.nameCount) {
                                score += 2; // strong signal: name index within declared range
                            } else if (nameIdx >= 0 && nameIdx < 1000) {
                                score += 1; // weak signal: small index (maybe local)
                            }
                        } else {
                            // нет информации о nameCount — все небольшие индексы считаем частично валидными
                            if (nameIdx >= 0 && nameIdx < 1000) score += 1;
                        }
                    }
                } catch (Throwable t) {
                    LOG.debug("validateExportEntries: objectName check failed for entry #{}: {}", idxCounter - 1, t.toString());
                }

                // 2) Проверка serialOffset/serialSize (bulk)
                try {
                    long sOff = ee.serialOffset;
                    long sSize = ee.serialSize;
                    boolean offOk = true;
                    if (sOff < 0 || sSize < 0) offOk = false;
                    if (fileLen > 0) {
                        if (sOff > fileLen) offOk = false;
                        if (sOff + sSize > fileLen) offOk = false;
                    }
                    // Нулевые offset/size могут означать отсутствие bulk — но это не обязательно делает запись недействительной.
                    if (offOk && (sSize > 0 || sOff > 0)) {
                        score += 2;
                    } else if (sSize == 0 && sOff == 0) {
                        // neutral: вероятно мета-only объект, даём небольшой +0
                    } else if (offOk) {
                        score += 1; // частично валидный (например offset в пределах файла, но размер 0)
                    }
                } catch (Throwable t) {
                    LOG.debug("validateExportEntries: bulk check failed for entry #{}: {}", idxCounter - 1, t.toString());
                }

                // 3) Проверка classIndex на разумность (опционально)
                try {
                    if (ee.classIndex != null) {
                        int cIdx = ee.classIndex.index;
                        if (header != null && header.nameCount > 0) {
                            if (cIdx > 0 && cIdx <= header.nameCount) {
                                score += 1;
                            }
                        } else {
                            if (cIdx >= 0 && cIdx < 1000) score += 1;
                        }
                    }
                } catch (Throwable t) {
                    LOG.debug("validateExportEntries: classIndex check failed for entry #{}: {}", idxCounter - 1, t.toString());
                }

                // Решение: считаем запись валидной если score >= 3 (хорошее сочетание сигналов)
                if (score >= 3) {
                    validCount++;
                } else {
                    LOG.debug("validateExportEntries: entry #{} considered invalid/weak (score={}) nameIdx={} size={} off={} classIdx={}",
                            idxCounter - 1,
                            score,
                            ee.objectName == null ? "<null>" : ee.objectName.index,
                            ee.serialSize,
                            ee.serialOffset,
                            ee.classIndex == null ? "<null>" : ee.classIndex.index);
                }
            }

            return validCount;
        }



        @Override
        public String name() { return "UE4Strategy"; }
    }

    // ---------------------------
    // UE5Strategy (best-effort)
    // ---------------------------
    private class UE5Strategy implements ParsingStrategy {
        @Override
        public boolean matches(HeaderProbe probe) {
            // UE5 often shares same tag but fileVersionUE4 may be >= certain threshold.
            // It's heuristic: if fileVersionUE4 >= 500 (example) assume UE5-ish; also check peeked ints patterns.
            if (probe.tag != UE4_TAG) return false;
            if (probe.fileVersionUE4 >= 500) return true; // heuristic threshold
            // else try to detect import/export offsets using peeked ints
            for (int i = 0; i + 5 < probe.peekedInts.length; i++) {
                if (plausibleCount(probe.peekedInts[i]) && plausibleOffset(probe.peekedInts[i+1]) &&
                        plausibleCount(probe.peekedInts[i+2]) && plausibleOffset(probe.peekedInts[i+3])) return true;
            }
            return false;
        }

        @Override
        public HeaderInfo readHeader(BinaryReader br, HeaderProbe probe) {
            // UE5's header layout differs; but we can use more conservative scan + validate nameOffset points to string entries.
            HeaderInfo h = new HeaderInfo();
            h.valid = false;
            try {
                // locate tag position first
                int tagPos = -1;
                for (int pos = 0; pos <= MAX_HEADER_SCAN - 4; pos += 4) {
                    try {
                        br.seek(pos);
                        if (br.readInt() == UE4_TAG) { tagPos = pos; break; }
                    } catch (IOException ex) { break; }
                }
                if (tagPos < 0) { fireDebug("UE5Strategy: tag not found"); return h; }
                fireDebug("UE5Strategy: tag at 0x" + Integer.toHexString(tagPos));

                // Scan a window after tag and test candidate tuples, but additionally validate nameOffset by peeking at that offset and checking strings
                final int WINDOW = 2048;
                for (int pos = tagPos + 4; pos <= tagPos + WINDOW - 24; pos += 4) {
                    try {
                        br.seek(pos);
                        int a = br.readInt(); int b = br.readInt(); int c = br.readInt(); int d = br.readInt(); int e = br.readInt(); int f = br.readInt();
                        if (!plausibleCount(a) || !plausibleOffset(b) || !plausibleCount(c) || !plausibleOffset(d)) continue;
                        // quick validation: check if nameOffset (b) indeed points to plausible FString sequence
                        if (!validateNameOffset(br, Integer.toUnsignedLong(b))) continue;
                        h.valid = true;
                        h.nameCount = a; h.nameOffset = Integer.toUnsignedLong(b);
                        h.exportCount = c; h.exportOffset = Integer.toUnsignedLong(d);
                        h.importCount = e; h.importOffset = Integer.toUnsignedLong(f);
                        h.headerSize = pos;
                        fireDebug("UE5Strategy: header matched at pos 0x" + Integer.toHexString(pos) + " nameOffset=0x" + Long.toHexString(h.nameOffset));
                        break;
                    } catch (IOException io) { break; }
                }
            } catch (Throwable t) {
                fireError("UE5Strategy.readHeader failed", t);
            }
            return h;
        }

        @Override
        public List<NameEntry> readNameMap(BinaryReader br, HeaderInfo header) {
            // UE5 still uses FString for names in many versions — try reading them carefully
            List<NameEntry> list = new ArrayList<>();
            if (!header.valid || header.nameCount <= 0) return list;
            try {
                br.seek(header.nameOffset);
                for (int i = 0; i < header.nameCount; i++) {
                    String name = br.readFString();
                    int flags = br.readInt();
                    list.add(new NameEntry(name == null ? "<null>" : name, flags));
                    if ((i & 0x3FF) == 0) firePercent((int) (i * 100L / Math.max(1, header.nameCount)));
                }
            } catch (Throwable t) {
                fireError("UE5Strategy.readNameMap failed", t);
            }
            return list;
        }

        @Override
        public List<ExportEntry> readExportMap(BinaryReader br, HeaderInfo header) {
            // UE5 export entry layout can differ; implement flexible reading:
            List<ExportEntry> exports = new ArrayList<>();
            if (!header.valid || header.exportCount <= 0) return exports;
            try {
                br.seek(header.exportOffset);
                for (int i = 0; i < header.exportCount; i++) {
                    long entryPos = br.position();
                    try {
                        // Try variant A (UE4-like)
                        int classIndexRaw = br.readInt();
                        int superIndexRaw = br.readInt();
                        int templateIndexRaw = br.readInt();
                        int outerIndexRaw = br.readInt();
                        int objectNameIndex = br.readInt();
                        int objectNameNumber = br.readInt();
                        int objectFlags = br.readInt();
                        long serialSize = br.readLong();
                        long serialOffset = br.readLong();

                        FName classIndex = new FName(Math.abs(classIndexRaw), 0);
                        FName superIndex = new FName(Math.abs(superIndexRaw), 0);
                        FName templateIndex = new FName(Math.abs(templateIndexRaw), 0);
                        FName objName = new FName(objectNameIndex, objectNameNumber);
                        ExportEntry ee = new ExportEntry(classIndex, superIndex, templateIndex, objName, serialSize, serialOffset, objectFlags);
                        exports.add(ee);
                    } catch (Throwable t1) {
                        // fallback: try smaller variant (e.g., some fields compressed) — for safety, break and abort
                        fireDebug("UE5Strategy: entry read failed at idx " + i + " pos=0x" + Long.toHexString(entryPos) + " : " + t1.getMessage());
                        break;
                    }
                    if ((i & 0x3FF) == 0) firePercent((int) (i * 100L / Math.max(1, header.exportCount)));
                }
            } catch (Throwable t) {
                fireError("UE5Strategy.readExportMap failed", t);
            }
            return exports;
        }

        @Override
        public String name() { return "UE5Strategy"; }

        private boolean validateNameOffset(BinaryReader br, long nameOffset) {
            try {
                if (nameOffset <= 0) return false;
                br.seek(nameOffset);
                // read first FString length int and ensure it looks plausible
                int len = br.readInt();
                if (len == 0) return true; // empty but plausible
                if (len < -1024 || len > 1024) return false;
                // read bytes of string safely (don't advance permanently)
                long cur = br.position();
                int toRead = Math.min(Math.abs(len), 256);
                byte[] sample = br.readBytes(toRead);
                // quick printable check
                int printable = 0;
                for (byte b : sample) {
                    int ub = b & 0xFF;
                    if (ub == 0) continue;
                    if (ub >= 32 || ub == 9 || ub == 10 || ub == 13) printable++;
                }
                br.seek(cur); // restore pointer
                return printable > 0;
            } catch (Throwable t) {
                return false;
            }
        }
    }

    // ---------------------------
    // HeuristicStrategy: fallback
    // ---------------------------
    private class HeuristicStrategy implements ParsingStrategy {

        @Override
        public boolean matches(HeaderProbe probe) {
            // Always matches as fallback
            return true;
        }

        @Override
        public HeaderInfo readHeader(BinaryReader br, HeaderProbe probe) {
            HeaderInfo h = new HeaderInfo();
            h.valid = false;
            // no header info known for heuristic
            return h;
        }

        @Override
        public List<NameEntry> readNameMap(BinaryReader br, HeaderInfo header) {
            List<NameEntry> names = new ArrayList<>();
            try {
                long scanBytes = Math.min(64 * 1024, br.position() + 64 * 1024);
                fireDebug("scanForNameMap(): scanning first " + scanBytes + " bytes");
                br.seek(0);
                int toRead = (int) Math.min(scanBytes, Integer.MAX_VALUE);
                byte[] data = br.readBytes(toRead);
                for (int i = 0; i + 8 < data.length; i++) {
                    int len = ((data[i] & 0xFF)) | ((data[i + 1] & 0xFF) << 8) | ((data[i + 2] & 0xFF) << 16) | ((data[i + 3] & 0xFF) << 24);
                    if (len <= 0 || len > 1024) continue;
                    int stringStart = i + 4;
                    int stringEnd = stringStart + len;
                    if (stringEnd + 4 > data.length) continue;
                    boolean printable = true;
                    for (int j = stringStart; j < Math.min(stringEnd, data.length); j++) {
                        int b = data[j] & 0xFF;
                        if (b == 0) continue;
                        if (b < 32 && b != 9 && b != 10 && b != 13) { printable = false; break; }
                    }
                    if (!printable) continue;
                    String name = new String(data, stringStart, Math.min(len, data.length - stringStart), java.nio.charset.StandardCharsets.UTF_8).replace("\0", "");
                    int flagsPos = stringEnd;
                    int flags = ((data[flagsPos] & 0xFF)) | ((data[flagsPos + 1] & 0xFF) << 8) | ((data[flagsPos + 2] & 0xFF) << 16) | ((data[flagsPos + 3] & 0xFF) << 24);
                    if (name.length() > 0 && name.length() < 256) {
                        names.add(new NameEntry(name, flags));
                        if (names.size() > 20000) break;
                        i = stringEnd + 3;
                    }
                }
                fireDebug("scanForNameMap(): found " + names.size() + " names heuristically");
            } catch (Throwable t) {
                fireError("HeuristicStrategy.readNameMap failed", t);
            }
            return names;
        }

        @Override
        public List<ExportEntry> readExportMap(BinaryReader br, HeaderInfo header) {
            List<ExportEntry> out = new ArrayList<>();
            try {
                // 1) Если header даёт явные оффсеты — попробуем парсить по ним, используя несколько вариантов layout'ов
                if (header != null && header.valid && header.exportCount > 0 && header.exportOffset > 0) {
                    fireDebug("HeuristicStrategy.readExportMap(): header present -> trying variant parses");
                    List<ExportEntry> va = tryParseExportVariantA(br, header);
                    int validA = validateExportEntries(va, header);
                    fireDebug("HeuristicStrategy: VariantA parsed " + va.size() + " entries, valid=" + validA);

                    List<ExportEntry> vb = tryParseExportVariantB(br, header);
                    int validB = validateExportEntries(vb, header);
                    fireDebug("HeuristicStrategy: VariantB parsed " + vb.size() + " entries, valid=" + validB);

                    if (validB > validA) {
                        fireDebug("HeuristicStrategy: choosing VariantB");
                        return vb;
                    } else {
                        fireDebug("HeuristicStrategy: choosing VariantA (best effort)");
                        return va;
                    }
                }

                // 2) Fallback: эвристический scan первых N байт (например, 256KB) — ищем повторяющиеся структуры, похожие на экспорт-записи.
                fireDebug("HeuristicStrategy.readExportMap(): header missing or exportCount==0 - performing byte-scan fallback");
                final int SCAN_BYTES = 256 * 1024; // 256KB
                br.seek(0);
                byte[] buffer;
                try {
                    buffer = br.readBytes(SCAN_BYTES); // может бросить EOF, тогда мы попадём в catch и пробуем меньшую выборку
                } catch (IOException ioe) {
                    // Попробуем прочитать то, что есть, уменьшая размер
                    int safeRead = 64 * 1024;
                    try {
                        br.seek(0);
                        buffer = br.readBytes(Math.min(safeRead, SCAN_BYTES));
                    } catch (IOException e2) {
                        fireDebug("HeuristicStrategy: can't read initial bytes for heuristic export scan: " + e2.getMessage());
                        return Collections.emptyList();
                    }
                }

                List<ExportEntry> found = parseExportsFromBytes(buffer);
                int valid = validateExportEntries(found, header);
                fireDebug("HeuristicStrategy: byte-scan found " + found.size() + " candidates, valid=" + valid);
                // если есть хоть один валидный — вернём их
                if (!found.isEmpty()) return found;

            } catch (Throwable t) {
                fireError("HeuristicStrategy.readExportMap failure", t);
            }
            fireDebug("HeuristicStrategy.readExportMap(): not implemented fully - returning empty list");
            return out;
        }

        /**
         * Variant A: typical UE4-like layout reading from header.exportOffset
         */
        private List<ExportEntry> tryParseExportVariantA(BinaryReader br, HeaderInfo header) {
            List<ExportEntry> res = new ArrayList<>();
            try {
                br.seek(header.exportOffset);
                for (int i = 0; i < header.exportCount; i++) {
                    long entryPos = br.position();
                    try {
                        int classIndexRaw = br.readInt();
                        int superIndexRaw = br.readInt();
                        int templateIndexRaw = br.readInt();
                        int outerIndexRaw = br.readInt();
                        int objectNameIndex = br.readInt();
                        int objectNameNumber = br.readInt();
                        int objectFlags = br.readInt();
                        long serialSize = br.readLong();
                        long serialOffset = br.readLong();

                        FName classIndex = new FName(Math.abs(classIndexRaw), 0);
                        FName superIndex = new FName(Math.abs(superIndexRaw), 0);
                        FName templateIndex = new FName(Math.abs(templateIndexRaw), 0);
                        FName objName = new FName(objectNameIndex, objectNameNumber);

                        res.add(new ExportEntry(classIndex, superIndex, templateIndex, objName, serialSize, serialOffset, objectFlags));
                    } catch (Throwable t) {
                        fireDebug("tryParseExportVariantA: stopped at index " + i + " pos=" + entryPos + " : " + t.getMessage());
                        break;
                    }
                }
            } catch (Throwable t) {
                fireDebug("tryParseExportVariantA: outer failure: " + t.getMessage());
            }
            return res;
        }

        /**
         * Variant B: compact/alternate layout reading from header.exportOffset (uses 32-bit serials)
         */
        private List<ExportEntry> tryParseExportVariantB(BinaryReader br, HeaderInfo header) {
            List<ExportEntry> res = new ArrayList<>();
            try {
                br.seek(header.exportOffset);
                for (int i = 0; i < header.exportCount; i++) {
                    long entryPos = br.position();
                    try {
                        int classIndexRaw = br.readInt();
                        int outerIndexRaw = br.readInt();
                        int objectNameIndex = br.readInt();
                        int objectNameNumber = br.readInt();
                        int objectFlags = br.readInt();
                        int serialSize32 = br.readInt();
                        int serialOffset32 = br.readInt();
                        long serialSize = Integer.toUnsignedLong(serialSize32);
                        long serialOffset = Integer.toUnsignedLong(serialOffset32);

                        FName classIndex = new FName(Math.abs(classIndexRaw), 0);
                        FName objName = new FName(objectNameIndex, objectNameNumber);

                        res.add(new ExportEntry(classIndex, null, null, objName, serialSize, serialOffset, objectFlags));
                    } catch (Throwable t) {
                        fireDebug("tryParseExportVariantB: stopped at index " + i + " pos=" + entryPos + " : " + t.getMessage());
                        break;
                    }
                }
            } catch (Throwable t) {
                fireDebug("tryParseExportVariantB: outer failure: " + t.getMessage());
            }
            return res;
        }

        /**
         * Validate export entries and return how many of them look "valid" (plausible).
         *
         * Scoring rules (per entry):
         *  - +2 if objectName.index is within [1..header.nameCount] (strong signal)
         *  - +1 if objectName.index is small but unknown (0..999) (weak signal)
         *  - +2 if serialOffset/serialSize look valid and within fileLen (strong signal)
         *  - +1 if classIndex.index looks reasonable (small or within nameCount) (weak signal)
         *
         * Entry considered valid when score >= 3.
         *
         * @param entries list of candidate export entries
         * @param header parsed header info (may contain nameCount)
         * @param fileLen length of the file used to validate serialOffset/serialSize (0 means unknown)
         * @return number of valid entries
         */
        private int validateExportEntries(List<ExportEntry> entries, HeaderInfo header, long fileLen) {
            if (entries == null || entries.isEmpty()) return 0;

            final int NAME_STRONG_SCORE = 2;
            final int NAME_WEAK_SCORE = 1;
            final int BULK_STRONG_SCORE = 2;
            final int CLASS_WEAK_SCORE = 1;

            int validCount = 0;
            int entryIndex = 0;

            // safe extraction of nameCount if available
            int nameCount = (header != null) ? header.nameCount : 0;

            for (ExportEntry ee : entries) {
                entryIndex++;
                if (ee == null) continue;
                int score = 0;

                // 1) objectName index check (strong signal)
                try {
                    if (ee.objectName != null) {
                        int nameIdx = ee.objectName.index;
                        if (nameCount > 0) {
                            if (nameIdx > 0 && nameIdx <= nameCount) {
                                score += NAME_STRONG_SCORE;
                            } else if (nameIdx >= 0 && nameIdx < 1000) {
                                score += NAME_WEAK_SCORE;
                            }
                        } else {
                            // no header.nameCount -> accept small indices as weak signal
                            if (nameIdx >= 0 && nameIdx < 1000) score += NAME_WEAK_SCORE;
                        }
                    }
                } catch (Throwable t) {
                    LOG.debug("validateExportEntries: objectName check failed for entry #{}: {}", entryIndex - 1, t.toString());
                }

                // 2) bulk (serialOffset/serialSize) check (strong signal)
                try {
                    long sOff = ee.serialOffset;
                    long sSize = ee.serialSize;
                    boolean offOk = true;

                    // Non-negative offsets/sizes only
                    if (sOff < 0 || sSize < 0) offOk = false;

                    // If fileLen known, ensure they are within bounds
                    if (fileLen > 0) {
                        if (sOff > fileLen) offOk = false;
                        if (sOff + sSize > fileLen) offOk = false;
                    }

                    // Score rules:
                    // - if both offset+size look OK and size>0 or offset>0 -> strong
                    // - if within bounds but size==0 and offset==0 -> neutral
                    // - if offset within bounds and size==0 -> weak
                    if (offOk && (sSize > 0 || sOff > 0)) {
                        score += BULK_STRONG_SCORE;
                    } else if (offOk && sSize == 0 && sOff == 0) {
                        // neutral: possibly meta-only object, do not give score
                    } else if (offOk) {
                        score += 1; // small positive signal
                    }
                } catch (Throwable t) {
                    LOG.debug("validateExportEntries: bulk check failed for entry #{}: {}", entryIndex - 1, t.toString());
                }

                // 3) classIndex plausibility (weak signal)
                try {
                    if (ee.classIndex != null) {
                        int cIdx = ee.classIndex.index;
                        if (nameCount > 0) {
                            if (cIdx > 0 && cIdx <= nameCount) score += CLASS_WEAK_SCORE;
                        } else {
                            if (cIdx >= 0 && cIdx < 1000) score += CLASS_WEAK_SCORE;
                        }
                    }
                } catch (Throwable t) {
                    LOG.debug("validateExportEntries: classIndex check failed for entry #{}: {}", entryIndex - 1, t.toString());
                }

                if (score >= 3) {
                    validCount++;
                } else {
                    // debug log weak/invalid entry
                    int nameIdx = ee.objectName == null ? -1 : ee.objectName.index;
                    long serialSize = ee.serialSize;
                    long serialOffset = ee.serialOffset;
                    int classIdx = ee.classIndex == null ? -1 : ee.classIndex.index;
                    LOG.debug("validateExportEntries: entry #{} weak/invalid (score={}) nameIdx={} size={} off={} classIdx={}",
                            entryIndex - 1, score, nameIdx, serialSize, serialOffset, classIdx);
                }
            }

            return validCount;
        }

        /**
         * Compatibility wrapper: try to obtain fileLen from header via reflection if present,
         * otherwise calls validateExportEntries(..., fileLen=0).
         *
         * This keeps existing call-sites working without changing their signatures.
         */
        private int validateExportEntries(List<ExportEntry> entries, HeaderInfo header) {
            long fileLen = 0L;
            if (header != null) {
                // Attempt to find common field names that might store file size
                String[] possibleNames = new String[] { "fileSize", "fileLen", "fileLength", "packageSize", "totalSize" };
                for (String n : possibleNames) {
                    try {
                        java.lang.reflect.Field f = header.getClass().getDeclaredField(n);
                        f.setAccessible(true);
                        Object val = f.get(header);
                        if (val instanceof Number) {
                            fileLen = ((Number) val).longValue();
                            break;
                        }
                    } catch (NoSuchFieldException ignored) {
                        // try next
                    } catch (Throwable t) {
                        LOG.debug("validateExportEntries: cannot read header field '{}': {}", n, t.toString());
                    }
                }
            }
            return validateExportEntries(entries, header, fileLen);
        }


        /**
         * Простая эвристика: сканирует байтовый буфер и пытается распознать повторяющиеся структуры,
         * которые выглядят как export-запись: [7 ints][2 longs] (пример) и возвращает кандидаты.
         *
         * Не гарантирует точность; нужен дополнительный validateExportEntries(...) для фильтрации.
         */
        private List<ExportEntry> parseExportsFromBytes(byte[] data) {
            List<ExportEntry> res = new ArrayList<>();
            if (data == null || data.length < 48) return res;
            java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i + 44 <= data.length; i += 4) { // slide by 4 bytes
                try {
                    int classIdx = bb.getInt(i);
                    int superIdx = bb.getInt(i + 4);
                    int templateIdx = bb.getInt(i + 8);
                    int outerIdx = bb.getInt(i + 12);
                    int nameIdx = bb.getInt(i + 16);
                    int nameNum = bb.getInt(i + 20);
                    int flags = bb.getInt(i + 24);
                    long serialSize = bb.getLong(i + 28); // careful: requires at least 8 bytes after pos 28 -> 36 total
                    // We read only serialSize here; serialOffset might follow (not enough bytes always)
                    // If there's enough room for serialOffset, read it:
                    long serialOffset = 0;
                    if (i + 36 + 8 <= data.length) {
                        serialOffset = bb.getLong(i + 36);
                    }

                    // Quick plausibility checks:
                    boolean plausible = false;
                    if (nameIdx > 0 && nameIdx < 200_000) plausible = true; // candidate name index reasonable
                    if (Math.abs(classIdx) < 1_000_000 && Math.abs(superIdx) < 1_000_000) plausible = true;
                    if (serialSize >= 0 && serialSize < (1L << 40)) plausible = true;

                    if (plausible) {
                        FName className = new FName(Math.abs(classIdx), 0);
                        FName objName = new FName(nameIdx, nameNum);
                        ExportEntry ee = new ExportEntry(className, new FName(Math.abs(superIdx), 0), new FName(Math.abs(templateIdx), 0), objName, serialSize, serialOffset, flags);
                        res.add(ee);
                    }
                } catch (Throwable ignore) {
                    // чтение вышло за границы — продолжаем
                }
            }
            return res;
        }


        @Override
        public String name() { return "HeuristicStrategy"; }
    }

    // ---------------------------
    // utilities
    // ---------------------------
    private boolean plausibleCount(int c) {
        return c >= 0 && c < 200_000;
    }
    private boolean plausibleOffset(int off) {
        return off >= 0 && (off < (1 << 30)); // <1GB
    }

    // ParseProgressListener for UI
    public interface ParseProgressListener {
        default void info(String msg) {}
        default void debug(String msg) {}
        default void error(String msg, Throwable t) {}
        default void percent(int p) {}
    }
}