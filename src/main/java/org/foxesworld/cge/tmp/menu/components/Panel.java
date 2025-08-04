package org.foxesworld.cge.tmp.menu.components;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class Panel implements MenuComponent, InteractiveComponent {

    private final Node panelNode;
    private final Geometry background;
    private final Style style;
    private final List<MenuComponent> children = new ArrayList<>();
    private final Vector2f position = new Vector2f();
    private static final Logger LOGGER = LoggerFactory.getLogger(Panel.class);

    private final String id; // Добавим ID для более информативного логирования
    private float width, height;
    private float padding, spacing;
    private float nextY; // Y-координата для следующего компонента

    public Panel(String id, AssetManager assetManager, Style style, float padding, float spacing) {
        this.id = id;
        this.style = style;
        this.padding = padding;
        this.spacing = spacing;
        this.panelNode = new Node("Panel_" + id);

        Material bgMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        bgMat.setColor("Color", style.backgroundColor);
        bgMat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        this.background = new Geometry("PanelBackground");
        this.background.setMaterial(bgMat);
        panelNode.attachChild(background);

        // --- 2. Логируем создание панели ---
        LOGGER.debug("Panel '{}' created with padding={}, spacing={}", id, padding, spacing);
    }

// В классе Panel.java

    /**
     * Adds a component to this panel. The panel will take control of the component's
     * size and position according to its internal layout rules.
     * @param component The component to add. Must not be null.
     */
    public void addComponent(MenuComponent component) {
        if (component == null) {
            LOGGER.warn("Attempted to add a null component to Panel '{}'.", this.id);
            return;
        }

        // Логируем по ID, это информативнее, чем по узлу
        LOGGER.debug("Adding component '{}' of type {} to Panel '{}'.",
                component.getNode(), component.getClass().getSimpleName(), this.id);

        // Добавляем в список и на сцену
        children.add(component);
        panelNode.attachChild(component.getNode());

        // Делегируем всю сложную работу по компоновке отдельному методу
        layoutChild(component);
    }

    /**
     * Calculates and applies the size and position for a single child component
     * based on the panel's layout rules (vertical stack).
     *
     * @param child The component to be laid out.
     */
    private void layoutChild(MenuComponent child) {
        // 1. Определяем ширину компонента.
        // Она всегда равна ширине панели минус боковые отступы.
        float componentWidth = this.width - (padding * 2);

        // 2. Определяем высоту компонента.
        // Это значение берется из самого компонента.
        // Он должен сам знать свою предпочтительную высоту.
        float componentHeight = child.getHeight();
        if (componentHeight <= 0) {
            LOGGER.warn("Component '{}' has a height of 0 or less. It may not be visible.", child.getNode());
        }

        // 3. Устанавливаем итоговый размер компонента.
        child.setSize(componentWidth, componentHeight);

        // 4. Вычисляем позицию внутри локальных координат панели.
        // X-координата - это просто левый отступ (padding).
        float localX = this.padding;
        // Y-координата - это текущая "каретка" минус высота самого компонента.
        float localY = this.nextY - componentHeight;

        // 5. Устанавливаем позицию.
        child.getNode().setLocalTranslation(localX, localY, 0);

        // 6. Сдвигаем "каретку" вниз для следующего компонента.
        this.nextY -= (componentHeight + this.spacing);
    }

    /**
     * Recalculates the layout for all existing child components.
     * This should be called whenever the panel's size changes.
     */
    private void relayout() {
        LOGGER.trace("Performing relayout for {} children in Panel '{}'.", children.size(), id);
        // Сбрасываем каретку в начальное положение (верхний край панели минус отступ)
        this.nextY = this.height - this.padding;

        // Просто вызываем layoutChild для каждого уже добавленного элемента.
        for (MenuComponent child : children) {
            layoutChild(child);
        }
    }

    public void setSize(float width, float height) {
        this.width = width;
        this.height = height;
        this.nextY = height - padding; // Начальная позиция для первого элемента

        background.setMesh(new RoundedQuad(width, height, style.cornerRadius, 16));
        background.setLocalTranslation(width / 2f, height / 2f, -1f);
    }

    public void setPosition(float x, float y) {
        this.position.set(x, y);
        panelNode.setLocalTranslation(x, y, 0);
    }

    @Override
    public void update(float tpf) {
        // Панель может иметь свою анимацию, но пока просто обновляем дочерние элементы
        for (MenuComponent child : children) {
            child.update(tpf);
        }
    }

    // Возвращаем дочерние компоненты, чтобы основной цикл мог проверять их на пересечение с мышью
    public List<MenuComponent> getChildren() {
        return children;
    }

    @Override public Node getNode() { return panelNode; }
    @Override public boolean intersects(Vector2f pos) { return false; } // Сама панель не интерактивна
    @Override public void setHovered(boolean hovered) {}
    @Override public void handleMousePress(Vector2f cursor) {}
    @Override public void handleMouseDrag(Vector2f cursor) {}
    @Override public void handleMouseRelease() {}

    @Override
    public float getHeight() {
        return this.height;
    }

    @Override
    public float getWidth() {
        return this.width;
    }

    @Override public void setActive(boolean active) {}


    /**
     * Defines the visual style of the Panel.
     */
    public record Style(ColorRGBA backgroundColor, float cornerRadius) {
        public static Style getDefaultStyle() {
            return new Style(new ColorRGBA(0.05f, 0.05f, 0.15f, 0.7f), 20f);
        }
    }
}