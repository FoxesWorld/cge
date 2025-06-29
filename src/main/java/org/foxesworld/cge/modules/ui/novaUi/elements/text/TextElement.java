package org.foxesworld.cge.modules.ui.novaUi.elements.text;

import com.jme3.font.BitmapText;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.modules.ui.novaUi.ElementRegistry;
import org.foxesworld.cge.modules.ui.novaUi.elements.AbstractUIElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.PropertyParser;
import org.foxesworld.cge.modules.ui.novaUi.elements.UIElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.panel.PanelElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

/**
 * A UI element for displaying text.
 * Its appearance and behavior are configured through properties.
 * It delegates text change animations to a configurable {@link ITextAnimator}.
 *
 * Supported Properties:
 * <ul>
 *   <li><b>text:</b> The string to display.</li>
 *   <li><b>fontPath:</b> Path to the .fnt file.</li>
 *   <li><b>fontSize:</b> The size of the font.</li>
 *   <li><b>color:</b> Color in "r,g,b,a" format (0-1).</li>
 *   <li><b>animation:</b> Type of animation ("instant", "fade"). Defaults to "instant".</li>
 *   <li><b>fadeDuration:</b> Duration in seconds for the fade animation.</li>
 *   <li><i>(Inherited layout properties like align, padding, margin are handled by parent panel)</i></li>
 * </ul>
 */
public class TextElement extends AbstractUIElement {

    private static final Logger LOGGER = LoggerFactory.getLogger(TextElement.class);

    private final BitmapText bitmapText;
    private ITextAnimator animator;

    public TextElement(CalistaGameEngine engine, ElementRegistry.CreationContext ctx, PanelElement parent) {
        super(engine, ctx.definition().getAttribute("id"), parent);
        this.node.setName("Text_" + id);
        String font = "Interface/Fonts/Default.fnt";
        // Default font settings, can be overridden by properties
        if(ctx.definition().getAttribute("fontPath") != null) {
            font = ctx.definition().getAttribute("fontPath");
        }
        this.bitmapText = new BitmapText(engine.getAssetManager().loadFont(font));
        this.node.attachChild(bitmapText);

        // Default animator is instant change.
        this.animator = new InstantTextAnimator(this.bitmapText);
    }

    @Override
    public void setProperty(String key, String value) {
        switch (key.toLowerCase()) {
            case "text" -> animator.setText(value);
            //case "fontpath" -> bitmapText.setFont(assetManager.loadFont(value));
            case "fontsize" -> bitmapText.setSize(Float.parseFloat(value));
            case "color" -> bitmapText.setColor(PropertyParser.parseColorRGBA(value));
            case "animation" -> setAnimator(value);

            // Allow animator-specific properties
            case "fadeduration" -> {
                if (animator instanceof FadeTextAnimator) {
                    // This is a bit of a hack. A better system might use a map of properties for the animator.
                    // For now, we recreate it.
                    this.animator = new FadeTextAnimator(this.bitmapText, Float.parseFloat(value));
                }
            }

            // Delegate layout properties to the abstract element
            default -> super.setProperty(key, value);
        }
    }

    private void setAnimator(String type) {
        float fadeDuration = 0.3f; // Default duration
        if (animator instanceof FadeTextAnimator oldAnimator) {
            // retain old duration if possible, though not implemented here
        }

        switch (type.toLowerCase()) {
            case "fade" -> this.animator = new FadeTextAnimator(this.bitmapText, fadeDuration);
            case "instant" -> this.animator = new InstantTextAnimator(this.bitmapText);
            default -> LOGGER.warn("Unknown animator type '{}' for TextElement '{}'. Using 'instant'.", type, getId());
        }
    }

    public void update(float tpf) {
        if (animator != null) {
            animator.update(tpf);
        }
    }

    @Override
    public float getWidth() {
        return bitmapText.getLineWidth();
    }

    @Override
    public float getHeight() {
        return bitmapText.getLineHeight() * bitmapText.getLineCount();
    }
}