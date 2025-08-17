package org.foxesworld.cge.tmp.menu.components;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;

/**
 * Абстрактный базовый класс для всех UI-компонентов.
 * Обеспечивает единый API и базовую реализацию.
 */
public abstract class UIComponent extends Node {

    protected String id, bind;
    private Object value;
    protected float dpiScale = 1.0f;
    private float width, height;

    protected UIComponent(String id) {
        super(id);
        this.id = id;
    }


    protected void drawBounds(AssetManager assetManager) {
        detachChildNamed("bounds");

        Node boundsNode = new Node("bounds");

        float w = getWidth();
        float h = getHeight();

        // координаты углов
        Vector3f topLeft     = new Vector3f(0,   h, 0);
        Vector3f topRight    = new Vector3f(w,   h, 0);
        Vector3f bottomLeft  = new Vector3f(0,   0, 0);
        Vector3f bottomRight = new Vector3f(w,   0, 0);

        // линии
        Geometry top = makeLine(assetManager, topLeft, topRight);
        Geometry bottom = makeLine(assetManager, bottomLeft, bottomRight);
        Geometry left = makeLine(assetManager, topLeft, bottomLeft);
        Geometry right = makeLine(assetManager, topRight, bottomRight);

        boundsNode.attachChild(top);
        boundsNode.attachChild(bottom);
        boundsNode.attachChild(left);
        boundsNode.attachChild(right);

        attachChild(boundsNode);
    }

    private Geometry makeLine(AssetManager assetManager, Vector3f start, Vector3f end) {
        Geometry geo = new Geometry("boundLine", new com.jme3.scene.shape.Line(start, end));

        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", ColorRGBA.Red);

        geo.setMaterial(mat);
        return geo;
    }


    public abstract void update(float tpf);
    public abstract boolean intersects(Vector2f cursor);
    public abstract void setSize(final float width, final float height);

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public Node getNode() {return this;}
    public float getDpiScale() {return dpiScale;}
    public void setDpiScale(float dpiScale) {this.dpiScale = dpiScale;}

    public void dispose() {
        removeFromParent();
        detachAllChildren();
    }

    public String getBind() {
        return bind;
    }

    public float getHeight() { return height; }
    public float getWidth() { return width; }
    public void setWidth(float width) {this.width = width;}
    public void setHeight(float height) {this.height = height;}

    public UIComponent getParentComponent() {
        if (getParent() instanceof UIComponent) {
            return (UIComponent) getParent();
        }
        return null;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }
}
