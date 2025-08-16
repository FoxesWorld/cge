package org.foxesworld.cge.tmp.menu.builders;

import com.jme3.math.Vector2f;
import com.jme3.scene.Node;
import org.foxesworld.cge.tmp.menu.BuildContext;
import org.foxesworld.cge.tmp.menu.MenuUtils;
import org.foxesworld.cge.tmp.menu.XmlMenuBuilder;
import org.foxesworld.cge.tmp.menu.components.*;
import org.foxesworld.cge.tmp.menu.components.utils.Orientation;
import org.foxesworld.cge.tmp.menu.xml.*;
import org.foxesworld.cge.tmp.menu.xml.ComponentBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a {@link ViceTabs} component from a {@link TabsXml} model.
 * This builder recursively calls the main XmlMenuBuilder to construct the content of each tab.
 */
public class TabsBuilder implements ComponentBuilder<TabsXml> {

    private final XmlMenuBuilder mainBuilder;

    /**
     * Constructs the TabsBuilder.
     * @param mainBuilder A reference to the main XmlMenuBuilder instance for recursive calls.
     */
    public TabsBuilder(XmlMenuBuilder mainBuilder) {
        this.mainBuilder = mainBuilder;
    }

    @Override
    public ViceTabs build(TabsXml model, Node parent, BuildContext context) {
        // Orientation: default to HORIZONTAL if unspecified or unknown
        Orientation orientation = "VERTICAL".equalsIgnoreCase(model.orientation)
                ? Orientation.VERTICAL
                : Orientation.HORIZONTAL;

        ViceTabs tabComponent = new ViceTabs(
                model.id,
                context,
                ViceButton.Style.getViceStyle(),
                orientation
        );

        // Spacing (fallback)
        float spacing = (model.spacing != 0) ? model.spacing : 8f;
        tabComponent.setSpacing(spacing);

        // Optional left padding used when positioning created components inside content area
        final float leftPadding = 25f;

        // We'll compute overall (max) content dimensions across all tabs so tabComponent can be finalized once.
        float overallContentWidth = 0f;
        float overallContentHeight = 0f;

        // Iterate tabs
        if (model.tabs != null) {
            for (TabXml tabModel : model.tabs) {

                Node contentNode = new Node("TabContent: " + (tabModel.title != null ? tabModel.title : "untitled"));
                List<UIComponent> createdComponents = new ArrayList<>();

                // Per-tab accumulators
                float tabMaxWidth = 0f;        // maximum single component width in this tab
                float tabMaxHeight = 0f;       // maximum single component height in this tab
                float tabTotalWidth = 0f;      // sum of widths (for horizontal layouts)
                float tabTotalHeight = 0f;     // sum of heights (for vertical layouts)
                int createdCount = 0;

                if (tabModel.components != null && !tabModel.components.isEmpty()) {
                    // First pass: create components and gather sizes
                    for (ComponentXml componentModel : tabModel.components) {
                        UIComponent createdComponent = mainBuilder.buildComponent(componentModel, contentNode);
                        if (createdComponent == null) continue;
                        createdComponents.add(createdComponent);

                        float w = createdComponent.getWidth();
                        float h = createdComponent.getHeight();

                        // guard against non-finite sizes
                        if (!Float.isFinite(w) || w < 0f) w = 0f;
                        if (!Float.isFinite(h) || h < 0f) h = 0f;

                        // update per-tab metrics
                        if (w > tabMaxWidth) tabMaxWidth = w;
                        if (h > tabMaxHeight) tabMaxHeight = h;

                        tabTotalWidth += w;
                        tabTotalHeight += h;

                        createdCount++;
                    }

                    // include spacing between components (n-1 gaps) in totals
                    if (createdCount > 1) {
                        float totalSpacing = spacing * (createdCount - 1);
                        tabTotalHeight += (orientation == Orientation.VERTICAL ? totalSpacing : 0f);
                        tabTotalWidth += (orientation == Orientation.HORIZONTAL ? totalSpacing : 0f);
                    }

                    // Determine the content area for this tab (respect model contentWidth / contentHeight if provided)
                    float contentW = (model.contentWidth > 0f) ? model.contentWidth : (orientation == Orientation.VERTICAL ? tabMaxWidth : tabTotalWidth);
                    float contentH = (model.contentHeight > 0f) ? model.contentHeight : (orientation == Orientation.VERTICAL ? tabTotalHeight : tabMaxHeight);

                    // Fallback sensible defaults if nothing created
                    if (contentW <= 0f) contentW = Math.max(tabMaxWidth, 64f);
                    if (contentH <= 0f) contentH = Math.max(tabMaxHeight, 64f);

                    // Update overall maxima
                    overallContentWidth = Math.max(overallContentWidth, contentW);
                    overallContentHeight = Math.max(overallContentHeight, contentH);

                    // Position components inside contentNode (second pass)
                    if (orientation == Orientation.VERTICAL) {
                        // stack top -> bottom inside content area
                        float cursorY = contentH; // start at top edge
                        for (UIComponent comp : createdComponents) {
                            float cw = contentW;         // in vertical layout, we typically stretch width to contentW
                            float ch = comp.getHeight();

                            // compute bottom Y coordinate inside content-local coords
                            float childBottomY = cursorY - ch;

                            // left padding applied; if need to horizontally center, compute (contentW - compWidth)/2
                            float childX = leftPadding;
                            // if we want to center each child horizontally:
                            // float childX = (contentW - comp.getWidth()) / 2f;

                            comp.getNode().setLocalTranslation(childX, childBottomY, 0f);

                            // advance cursor downward (height + spacing)
                            cursorY = childBottomY - spacing;
                        }
                    } else {
                        // horizontal layout: left -> right
                        float cursorX = leftPadding;
                        float alignTop = contentH;
                        for (UIComponent comp : createdComponents) {
                            float cw = comp.getWidth();
                            float ch = comp.getHeight();

                            // align to top inside content area
                            float childBottomY = alignTop - ch;

                            comp.getNode().setLocalTranslation(cursorX, childBottomY, 0f);

                            cursorX += cw + spacing;
                        }
                    }

                } else {
                    // no components — use defaults or model-defined content sizes
                    float contentW = (model.contentWidth > 0f) ? model.contentWidth : 128f;
                    float contentH = (model.contentHeight > 0f) ? model.contentHeight : 64f;
                    overallContentWidth = Math.max(overallContentWidth, contentW);
                    overallContentHeight = Math.max(overallContentHeight, contentH);
                }

                // Add tab to component (tabComponent will own the contentNode)
                tabComponent.addTab(tabModel, contentNode, createdComponents);
            }
        }

        // After processing all tabs, finalize layout using the largest content dimensions (so tabs container fits the biggest)
        float finalContentWidth = (model.contentWidth > 0f) ? model.contentWidth : overallContentWidth;
        float finalContentHeight = (model.contentHeight > 0f) ? model.contentHeight : overallContentHeight;

        // Finalize once — informs the tabs component how much content area to reserve/show
        tabComponent.finalizeLayout(finalContentWidth, finalContentHeight);

        // Position the whole tabs component in parent as before (center by model.width if provided)
        Vector2f pos = MenuUtils.calculatePosition(model.x, model.y, model.alignX, context.mainMenuAppState().getGameEngine().getCamera());
        // compute outer width used for centering: prefer model.width if present, otherwise rely on tabs width or content width
        float outerWidth = MenuUtils.parseSize(model.width, context.mainMenuAppState().getGameEngine().getCamera().getWidth());
        if (outerWidth <= 0f) {
            // Prefer tabComponent.getWidth() if it reports an actual size, else fallback to content width
            float reported = tabComponent.getWidth();
            outerWidth = (reported > 0f) ? reported : finalContentWidth;
        }
        pos.x -= outerWidth / 2f;

        tabComponent.getNode().setLocalTranslation(pos.x, pos.y, 0f);
        parent.attachChild(tabComponent.getNode());

        return tabComponent;
    }

}