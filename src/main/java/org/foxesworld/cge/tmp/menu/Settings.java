package org.foxesworld.cge.tmp.menu;

@SuppressWarnings("unused")
public class Settings {

    public Settings() {}

    public class graphics {
        public boolean vsync = true;
        public boolean shadows = true;
        public boolean bloom = true;
        public boolean ssao = true;
    }

    public class audio {
        public float master = 1.0f;
        public float music = 0.5f;
    }

    public class controls {
        public float sensitivity = 0.5f;
    }

    private final graphics graphics = new graphics();
    private final audio audio = new audio();
    private final controls controls = new controls();

    public graphics getGraphics() { return graphics; }
    public audio getAudio() { return audio; }
    public controls getControls() { return controls; }
}
