package org.foxesworld.cge.modules.renderer;

import com.jme3.renderer.Caps;
import com.jme3.renderer.Limits;
import com.jme3.renderer.Renderer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30; // <-- НОВЫЙ ИМПОРТ
import org.lwjgl.system.MemoryUtil;

import java.util.*;

import static org.lwjgl.opengl.ATIMeminfo.GL_VBO_FREE_MEMORY_ATI;
import static org.lwjgl.opengl.NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX;
import static org.lwjgl.opengl.NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX;

public class GpuInfo {
    // --- Константы для форматирования (без изменений) ---
    private static final int BLOCK_WIDTH = 68;
    private static final String VERTICAL = "║";
    private static final String HORIZONTAL = "═";
    private static final String CORNER_TL = "╔";
    private static final String CORNER_TR = "╗";
    private static final String CORNER_BL = "╚";
    private static final String CORNER_BR = "╝";
    private static final String TEE_L = "╠";
    private static final String TEE_R = "╣";
    private static final String TEE_C = "╬";
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN = "\u001B[36m";
    private static final String MAGENTA = "\u001B[35m";
    private static final String WHITE = "\u001B[37m";

    // --- Поля класса (без изменений) ---
    private final String glVendor;
    private final String glRenderer;
    private final String glVersion;
    private final String glslVersion;
    private Set<String> extensions;
    private final EnumSet<Caps> caps;
    private final EnumMap<Limits, Integer> limits;
    private final int totalVramMb;
    private final int availableVramMb;
    private final String primaryMonitorName;
    private final String primaryMonitorResolution;
    private final int primaryMonitorRefreshRate;

    public GpuInfo(Renderer renderer) {
        // Инициализация по умолчанию
        this.caps = EnumSet.noneOf(Caps.class);
        this.limits = new EnumMap<>(Limits.class);

        String vendorTemp = "<unknown>", rendererTemp = "<unknown>", versionTemp = "<unknown>", glslTemp = "<unknown>";
        String monitorName = "<unknown>", monitorRes = "<unknown>";
        int monitorHz = 0;
        int[] vram = {0, 0};

        if (renderer != null && GL.getCapabilities() != null) {
            try {
                this.caps.addAll(renderer.getCaps());
                this.limits.putAll(renderer.getLimits());

                vendorTemp = GL11.glGetString(GL11.GL_VENDOR);
                rendererTemp = GL11.glGetString(GL11.GL_RENDERER);
                versionTemp = GL11.glGetString(GL11.GL_VERSION);

                if (GL.getCapabilities().OpenGL20) {
                    glslTemp = GL11.glGetString(GL20.GL_SHADING_LANGUAGE_VERSION);
                } else {
                    glslTemp = "N/A (OpenGL < 2.0)";
                }

                // --- ИЗМЕНЕНИЕ ЗДЕСЬ: Вызываем новый метод для получения расширений ---
                this.extensions = fetchAllExtensions();

                vram = queryVramInfo();

                long primaryMonitor = GLFW.glfwGetPrimaryMonitor();
                if (primaryMonitor != MemoryUtil.NULL) {
                    monitorName = GLFW.glfwGetMonitorName(primaryMonitor);
                    GLFWVidMode vidMode = GLFW.glfwGetVideoMode(primaryMonitor);
                    if (vidMode != null) {
                        monitorRes = vidMode.width() + "x" + vidMode.height();
                        monitorHz = vidMode.refreshRate();
                    }
                }

            } catch (Exception e) {
                System.err.println("Could not query full GPU info, context may not be ready: " + e.getMessage());
            }
        } else {
            // Если контекст не готов, инициализируем пустой набор расширений
            this.extensions = Collections.emptySet();
        }

        this.glVendor = vendorTemp;
        this.glRenderer = rendererTemp;
        this.glVersion = versionTemp;
        this.glslVersion = glslTemp;
        this.totalVramMb = vram[0];
        this.availableVramMb = vram[1];
        this.primaryMonitorName = monitorName;
        this.primaryMonitorResolution = monitorRes;
        this.primaryMonitorRefreshRate = monitorHz;
    }

    /**
     * Получает полный список расширений OpenGL, используя современный метод (glGetStringi),
     * если он доступен, или устаревший метод в качестве запасного.
     *
     * @return Множество строк с именами расширений.
     */
    private Set<String> fetchAllExtensions() {
        Set<String> extensionSet = new HashSet<>();
        if (GL.getCapabilities().OpenGL30) {
            // Современный, правильный способ (для OpenGL 3.0+)
            int numExtensions = GL11.glGetInteger(GL30.GL_NUM_EXTENSIONS);
            for (int i = 0; i < numExtensions; i++) {
                extensionSet.add(GL30.glGetStringi(GL11.GL_EXTENSIONS, i));
            }
        } else {
            // Устаревший способ (для OpenGL < 3.0)
            String extStr = GL11.glGetString(GL11.GL_EXTENSIONS);
            if (extStr != null) {
                extensionSet.addAll(Arrays.asList(extStr.split(" ")));
            }
        }
        return extensionSet;
    }

    // ... (остальные методы класса: queryVramInfo, formatGpuInfo, и т.д. остаются без изменений) ...
    // Я скопирую их для полноты

    private int[] queryVramInfo() {
        if (extensions.contains("GL_NVX_gpu_memory_info")) {
            int total = GL11.glGetInteger(GL_GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX) / 1024;
            int available = GL11.glGetInteger(GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX) / 1024;
            return new int[]{total, available};
        }
        if (extensions.contains("GL_ATI_meminfo")) {
            int available = GL11.glGetInteger(GL_VBO_FREE_MEMORY_ATI) / 1024;
            return new int[]{0, available};
        }
        return new int[]{0, 0};
    }

    public String formatGpuInfo() {
        StringJoiner info = new StringJoiner("\n");
        info.add(borderLine(CORNER_TL, CORNER_TR));
        info.add(centerLine(BOLD + YELLOW + "GPU & Renderer Capabilities" + RESET));
        info.add(borderLine(TEE_L, TEE_R, TEE_C));
        info.add(kvLine("Vendor", CYAN + glVendor + RESET));
        info.add(kvLine("Renderer", CYAN + glRenderer + RESET));
        info.add(kvLine("Version", CYAN + glVersion + RESET));
        info.add(kvLine("GLSL Version", CYAN + glslVersion + RESET));
        info.add(borderLine(TEE_L, TEE_R, TEE_C));
        info.add(centerLine(BOLD + MAGENTA + "VRAM Usage" + RESET));
        if (totalVramMb > 0) {
            info.add(formatVramLine());
        } else if (availableVramMb > 0) {
            info.add(kvLine("Available", String.format("%d MB", availableVramMb) + YELLOW + " (Total Unknown)" + RESET));
        } else {
            info.add(centerLine(RED + "VRAM info not available" + RESET));
        }
        info.add(borderLine(TEE_L, TEE_R, TEE_C));
        info.add(centerLine(BOLD + MAGENTA + "Primary Display" + RESET));
        info.add(kvLine("Monitor", WHITE + primaryMonitorName + RESET));
        info.add(kvLine("Resolution", WHITE + String.format("%s @ %d Hz", primaryMonitorResolution, primaryMonitorRefreshRate) + RESET));
        info.add(borderLine(TEE_L, TEE_R, TEE_C));
        info.add(centerLine(BOLD + YELLOW + "Key Features & Extensions" + RESET));
        addFeature(info, "OpenGL 3.0+", caps.contains(Caps.OpenGL30));
        addFeature(info, "OpenGL 4.0+", caps.contains(Caps.OpenGL40));
        addFeature(info, "Instancing", caps.contains(Caps.MeshInstancing));
        addFeature(info, "Geometry Shaders", caps.contains(Caps.GeometryShader));
        addFeature(info, "Compute Shaders", extensions.contains("GL_ARB_compute_shader"));
        addFeature(info, "Mesh Shaders", extensions.contains("GL_NV_mesh_shader") || extensions.contains("GL_EXT_mesh_shader"));
        addFeature(info, "Bindless Textures", extensions.contains("GL_ARB_bindless_texture"));
        addFeature(info, "Anisotropic Filtering", extensions.contains("GL_EXT_texture_filter_anisotropic"));
        addFeature(info, "Direct State Access (DSA)", extensions.contains("GL_ARB_direct_state_access"));
        addFeature(info, "Debug Output (KHR)", extensions.contains("GL_KHR_debug"));
        info.add(borderLine(TEE_L, TEE_R, TEE_C));
        info.add(centerLine(BOLD + YELLOW + "Renderer Limits" + RESET));
        limits.keySet().stream().sorted(Comparator.comparing(Enum::name)).forEach(limit -> {
            info.add(limitLine(limit.name(), limits.get(limit)));
        });
        info.add(borderLine(CORNER_BL, CORNER_BR));
        return info.toString();
    }

    private String formatVramLine() {
        int usedVram = totalVramMb - availableVramMb;
        double usedPercent = (double) usedVram / totalVramMb;
        String color = usedPercent > 0.85 ? RED : (usedPercent > 0.6 ? YELLOW : GREEN);
        String bar = createProgressBar(usedPercent, 20);
        String text = String.format("%s%d / %d MB%s (%d%%) %s", color, usedVram, totalVramMb, RESET, (int) (usedPercent * 100), bar);
        return kvLine("Usage", text);
    }

    private static String createProgressBar(double percent, int width) {
        int filledCount = (int) (percent * width);
        return "[" + "■".repeat(filledCount) + " ".repeat(width - filledCount) + "]";
    }
    private static String borderLine(String left, String right) {
        return left + HORIZONTAL.repeat(BLOCK_WIDTH - 2) + right;
    }
    private static String borderLine(String left, String right, String tee) {
        int mid = BLOCK_WIDTH / 2;
        return left + HORIZONTAL.repeat(mid - 1) + tee + HORIZONTAL.repeat(BLOCK_WIDTH - mid - 2) + right;
    }
    private static String centerLine(String text) {
        int textLen = stripAnsi(text).length();
        int padL = (BLOCK_WIDTH - 2 - textLen) / 2;
        return VERTICAL + " ".repeat(padL) + text + " ".repeat(BLOCK_WIDTH - 2 - textLen - padL) + VERTICAL;
    }
    private static String kvLine(String key, String value) {
        String line = String.format("  %-14s: %s", key, value);
        return VERTICAL + line + " ".repeat(Math.max(0, BLOCK_WIDTH - 2 - stripAnsi(line).length())) + VERTICAL;
    }
    private static void addFeature(StringJoiner sj, String name, boolean supported) {
        String mark = supported ? GREEN + "✔ Supported" + RESET : RED + "✘ Not Supported" + RESET;
        String featureStr = String.format("   %-28s : %s", name, mark);
        sj.add(VERTICAL + featureStr + " ".repeat(Math.max(0, BLOCK_WIDTH - 2 - stripAnsi(featureStr).length())) + VERTICAL);
    }
    private static String limitLine(String name, int value) {
        String nameFmt = name.length() > 32 ? name.substring(0, 32) : name;
        String valueStr = String.format("%,d", value);
        String limitStr = String.format("   %-32s : %12s", nameFmt, valueStr);
        return VERTICAL + limitStr + " ".repeat(Math.max(0, BLOCK_WIDTH - 2 - stripAnsi(limitStr).length())) + VERTICAL;
    }
    private static String stripAnsi(String s) {
        return s == null ? "" : s.replaceAll("\\u001B\\[[;\\d]*m", "");
    }
}