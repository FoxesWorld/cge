package org.foxesworld.cge.importers.fbx.anim;

import org.foxesworld.cge.importers.fbx.FBXNode;

import java.util.ArrayList;
import java.util.List;

class FBXKeyframe {
    public float[] times;
    public float[] values;

    public static FBXKeyframe fromFBXNode(FBXNode node) {
        FBXKeyframe kf = new FBXKeyframe();
        for (FBXNode child : node.getChildren()) {
            if (child.getName().equals("KeyTime")) {
                kf.times = parseFloatArray(child.getProperties());
            }
            if (child.getName().equals("KeyValueFloat")) {
                kf.values = parseFloatArray(child.getProperties());
            }
        }
        return kf;
    }

    private static float[] parseFloatArray(List<String> props) {
        if (props.isEmpty()) return null;
        String[] split = props.get(0).replaceAll("[A-Za-z\\s=]", "").split(",");
        List<Float> list = new ArrayList<>();
        for (String s : split) {
            if (!s.trim().isEmpty()) list.add(Float.parseFloat(s.trim()));
        }
        float[] arr = new float[list.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = list.get(i);
        return arr;
    }
}
