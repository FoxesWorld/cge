package org.foxesworld.cge.tmp.menu.components;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;

import java.util.ArrayList;
import java.util.List;

public class Panel implements MenuComponent, InteractiveComponent {

    private final Node panelNode;
    private final Geometry background;
    private final Style style;
    private final List<MenuComponent> children = new ArrayList<>();
    private final Vector2f position = new Vector2f();

    private float width, height;
    private float padding, spacing;
    private float nextY; // Y-координата для следующего компонента

    public Panel(AssetManager assetManager, Style style, float padding, float spacing) {
        this.style = style;
        this.padding = padding;
        this.spacing = spacing;
        this.panelNode = new Node("Panel");

        Material bgMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        bgMat.setColor("Color", style.backgroundColor);
        bgMat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        this.background = new Geometry("PanelBackground");
        this.background.setMaterial(bgMat);
        panelNode.attachChild(background);
    }

    /**
     * Adds a component to the panel and positions it according to the layout.
     * @param component The component to add (e.g., a ViceButton).
     */
    public void addComponent(MenuComponent component) {
        if (!(component instanceof ViceButton button)) {
            // Можно расширить для поддержки других типов, если нужно
            return;
        }

        children.add(component);
        panelNode.attachChild(component.getNode());

        // Layout logic (simple vertical stack)
        float buttonHeight = button.getHeight();
        float buttonWidth = this.width - (padding * 2); // Кнопка занимает всю ширину панели минус отступы

        button.setSize(buttonWidth, buttonHeight);
        button.setPosition(padding, nextY - buttonHeight);

        // Обновляем Y для следующего компонента
        nextY -= (buttonHeight + spacing);
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