package org.foxesworld.cge.modules.inputManager;

import org.foxesworld.cge.core.module.ModuleConfig;

import java.util.HashMap;
import java.util.Map;


public class InputConfig extends ModuleConfig {

        private float mouseSensitivity = 1.0f;
        private boolean invertMouseY = false;
        private Map<String, String> keyMappings = new HashMap<>();

        public float getMouseSensitivity() {
            return mouseSensitivity;
        }

        public void setMouseSensitivity(float mouseSensitivity) {
            this.mouseSensitivity = mouseSensitivity;
        }

        public boolean isİnvertMouseY() {
            return invertMouseY;
        }

        public void setInvertMouseY(boolean invertMouseY) {
            this.invertMouseY = invertMouseY;
        }

        public Map<String, String> getKeyMappings() {
            return keyMappings;
        }

        public void setKeyMappings(Map<String, String> keyMappings) {
            this.keyMappings = keyMappings;
        }
}
