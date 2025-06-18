package org.foxesworld.cge.tmp;

import com.jme3.input.RawInputListener;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.input.InputManager;
import com.jme3.scene.control.AbstractControl;
import com.jme3.scene.Spatial;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread-safe raw key capture for 'C' key press, with callback executed on the render thread.
 */
public class RawKeyCaptureControl extends AbstractControl {
    private final InputManager input;
    private final Callback callback;
    private final AtomicBoolean isRegistered = new AtomicBoolean(false);
    private final AtomicBoolean keyCPressed = new AtomicBoolean(false);

    public interface Callback {
        void onKeyC();
        // Дополнительные методы для других клавиш
    }

    public RawKeyCaptureControl(InputManager input, Callback callback) {
        this.input = input;
        this.callback = callback;
        registerListener();
    }

    private void registerListener() {
        if (isRegistered.compareAndSet(false, true)) {
            input.addRawInputListener(rawListener);
        }
    }

    private void unregisterListener() {
        if (isRegistered.compareAndSet(true, false)) {
            input.removeRawInputListener(rawListener);
        }
    }

    private final RawInputListener rawListener = new RawInputListener() {
        @Override public void beginInput() {}
        @Override public void endInput() {}
        @Override public void onJoyAxisEvent(com.jme3.input.event.JoyAxisEvent evt) {}
        @Override public void onJoyButtonEvent(com.jme3.input.event.JoyButtonEvent evt) {}
        @Override public void onMouseMotionEvent(com.jme3.input.event.MouseMotionEvent evt) {}
        @Override public void onMouseButtonEvent(com.jme3.input.event.MouseButtonEvent evt) {}
        @Override public void onTouchEvent(com.jme3.input.event.TouchEvent evt) {}

        @Override
        public void onKeyEvent(KeyInputEvent evt) {
            if (evt.getKeyCode() == com.jme3.input.KeyInput.KEY_C && evt.isPressed()) {
                keyCPressed.set(true);
                evt.setConsumed();
            }
        }
    };

    @Override
    protected void controlUpdate(float tpf) {
        // В main-потоке JME проверяем и вызываем callback
        if (keyCPressed.getAndSet(false)) {
            callback.onKeyC();
        }
    }

    @Override
    protected void controlRender(com.jme3.renderer.RenderManager rm, com.jme3.renderer.ViewPort vp) {
        // нет необходимости
    }

    @Override
    public void setSpatial(Spatial spatial) {
        super.setSpatial(spatial);
        if (spatial == null) {
            unregisterListener();
        } else {
            registerListener();
        }
    }

    @Override
    public void finalize() {
        unregisterListener();
    }
}