package org.foxesworld.cge.modules.renderer;

import com.jme3.renderer.Caps;
import com.jme3.renderer.Limits;
import com.jme3.renderer.Renderer;
import org.lwjgl.opengl.GL11;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.StringJoiner;

public class GpuInfo {
    private static final int BLOCK_WIDTH = 56;
    private static final String VERTICAL = "║";
    private static final String HORIZONTAL = "═";
    private static final String CORNER_TL = "╔";
    private static final String CORNER_TR = "╗";
    private static final String CORNER_BL = "╚";
    private static final String CORNER_BR = "╝";
    private static final String TEE = "╠";
    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN = "\u001B[36m";

    private final EnumSet<Caps> caps;
    private final EnumMap<Limits, Integer> limits;
    private final String glVendor;
    private final String glRenderer;
    private final String glVersion;

    public GpuInfo(Renderer renderer) {
        EnumSet<Caps> capsTemp = EnumSet.noneOf(Caps.class);
        EnumMap<Limits, Integer> limitsTemp = new EnumMap<>(Limits.class);
        String vendorTemp = "<unknown>", rendererTemp = "<unknown>", versionTemp = "<unknown>";

        if (renderer != null) {
            try {
                capsTemp = renderer.getCaps();
                limitsTemp = renderer.getLimits();
            } catch (Throwable t) {
                // ignore
            }
        }
        try {
            vendorTemp = GL11.glGetString(GL11.GL_VENDOR);
            rendererTemp = GL11.glGetString(GL11.GL_RENDERER);
            versionTemp = GL11.glGetString(GL11.GL_VERSION);
        } catch (Throwable t) {
            // OpenGL context may not be ready
        }
        this.caps = capsTemp;
        this.limits = limitsTemp;
        this.glVendor = vendorTemp;
        this.glRenderer = rendererTemp;
        this.glVersion = versionTemp;
    }

    public String formatGpuInfo() {
        StringJoiner info = new StringJoiner("\n");
        info.add(borderLine(CORNER_TL, CORNER_TR));
        info.add(centerLine(YELLOW + "GPU & Renderer Capabilities" + RESET));
        info.add(borderLine(TEE, TEE));
        info.add(kvLine("Vendor", CYAN + glVendor + RESET));
        info.add(kvLine("Renderer", CYAN + glRenderer + RESET));
        info.add(kvLine("Version", CYAN + glVersion + RESET));
        info.add(borderLine(TEE, TEE));
        info.add(leftLine(YELLOW + "Supported Features:" + RESET));
        addFeature(info, "GLSL 1.0", caps.contains(Caps.GLSL100));
        addFeature(info, "GLSL 1.3+", caps.contains(Caps.GLSL130) || caps.contains(Caps.GLSL150));
        addFeature(info, "FrameBuffer", caps.contains(Caps.FrameBuffer));
        addFeature(info, "Geometry Shader", caps.contains(Caps.GeometryShader));
        addFeature(info, "Texture Array", caps.contains(Caps.TextureArray));
        addFeature(info, "Multisample", caps.contains(Caps.Multisample));
        addFeature(info, "OpenGL 3.0+", caps.contains(Caps.OpenGL30));
        addFeature(info, "OpenGL 4.0+", caps.contains(Caps.OpenGL40));
        addFeature(info, "Instancing", caps.contains(Caps.MeshInstancing));
        addFeature(info, "Depth Texture", caps.contains(Caps.DepthTexture));
        addFeature(info, "sRGB", caps.contains(Caps.Srgb));
        info.add(borderLine(TEE, TEE));
        info.add(leftLine(YELLOW + "Renderer Limits:" + RESET));
        limits.keySet().stream().sorted(Comparator.comparing(Enum::name)).forEach(limit -> {
            info.add(limitLine(limit.name(), limits.get(limit)));
        });
        info.add(borderLine(CORNER_BL, CORNER_BR));
        return info.toString();
    }

    private static String borderLine(String left, String right) {
        return left + HORIZONTAL.repeat(BLOCK_WIDTH - 2) + right;
    }

    private static String centerLine(String text) {
        int pad = (BLOCK_WIDTH - 2 - stripAnsi(text).length()) / 2;
        int padR = (BLOCK_WIDTH - 2 - stripAnsi(text).length()) - pad;
        return VERTICAL + " ".repeat(pad) + text + " ".repeat(padR) + VERTICAL;
    }

    private static String leftLine(String text) {
        return VERTICAL + "  " + text + " ".repeat(BLOCK_WIDTH - 4 - stripAnsi(text).length()) + VERTICAL;
    }

    private static String kvLine(String key, String value) {
        String keyFmt = String.format("%-9s", key);
        String valFmt = String.format("%-32s", stripAnsi(value).length() > 32 ? stripAnsi(value).substring(0, 32) : value);
        String line = String.format("  %s   : %s", keyFmt, valFmt);
        return VERTICAL + line + " ".repeat(BLOCK_WIDTH - 2 - stripAnsi(line).length()) + VERTICAL;
    }

    private static void addFeature(StringJoiner sj, String name, boolean supported) {
        final int featWidth = 26;
        String nameTrimmed = name.length() > featWidth ? name.substring(0, featWidth) : name;
        String mark = supported ? GREEN + "✔" + RESET : RED + "✘" + RESET;
        String featureStr = String.format("   %-26s : %-1s", nameTrimmed, mark);
        sj.add(VERTICAL + featureStr + " ".repeat(BLOCK_WIDTH - 2 - stripAnsi(featureStr).length()) + VERTICAL);
    }

    private static String limitLine(String name, int value) {
        final int nameWidth = 28;
        String nameTrimmed = name.length() > nameWidth ? name.substring(0, nameWidth) : name;
        String valueStr = String.format("%8d", value);
        String limitStr = String.format("   %-28s :%7s", nameTrimmed, valueStr);
        return VERTICAL + limitStr + " ".repeat(BLOCK_WIDTH - 2 - stripAnsi(limitStr).length()) + VERTICAL;
    }

    /** Utility: removes ANSI color codes for length calculation. */
    private static String stripAnsi(String s) {
        return s == null ? "" : s.replaceAll("\\u001B\\[[;\\d]*m", "");
    }
}