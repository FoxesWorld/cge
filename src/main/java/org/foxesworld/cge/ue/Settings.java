package org.foxesworld.cge.ue;

public class Settings {

    public Settings() {}

    public class graphics { public boolean vsync; public boolean shadows; public boolean bloom; }
    public class audio { public float master; public float music; }
    public class controls { public float sensitivity; }

    private final graphics graphics = new graphics();
    private final audio audio = new audio();
    private final controls controls = new controls();

    public graphics getGraphics() { return graphics; }
    public audio getAudio() { return audio; }
    public controls getControls() { return controls; }
}
