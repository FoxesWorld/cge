package org.foxesworld.cge.ui.novaUi;

import java.lang.reflect.Method;

/**
 * OnClickHandler умеет вызвать метод methodName() на объекте target через Reflection.
 */
public class OnClickHandler {
    private final String methodName;
    private final Object target;

    public OnClickHandler(String methodName, Object target) {
        this.methodName = methodName;
        this.target = target;
    }

    public void invoke() {
        if (target == null || methodName == null) {
            return;
        }
        try {
            Method m = target.getClass().getMethod(methodName);
            m.invoke(target);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke click handler: " + methodName, e);
        }
    }
}
