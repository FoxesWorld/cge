package org.foxesworld.cge.importers.fbx.anim;

import org.foxesworld.cge.importers.fbx.FBXNode;

import java.util.*;

public class FBXAnimation {
    public String name;
    public List<FBXKeyframe> keyframes = new ArrayList<>();

    public static FBXAnimation fromFBXNode(FBXNode node) {
        FBXAnimation anim = new FBXAnimation();
        anim.name = node.getProperties().isEmpty() ? node.getName() : node.getProperties().get(0);
        // Для MVP: ищем AnimationCurve с ключевыми кадрами
        for (FBXNode child : node.getChildren()) {
            if (child.getName().equals("AnimationCurve")) {
                FBXKeyframe kf = FBXKeyframe.fromFBXNode(child);
                anim.keyframes.add(kf);
            }
        }
        return anim;
    }
}

