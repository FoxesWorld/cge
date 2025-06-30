package org.foxesworld.cge.modules.sound;

import com.jme3.app.Application;
import com.jme3.audio.AudioData;
import com.jme3.audio.AudioNode;
import com.jme3.audio.Listener;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.module.EngineModule;

/**
 * Стандартный модуль звука для JME3.
 * Использует только стандартный AudioNode/Listener.
 */
public class SoundModule extends EngineModule<SoundConfig> {

    private final Node soundRootNode = new Node("SoundRootNode");

    public SoundModule(CalistaGameEngine app) {
        super(SoundModule.class, SoundConfig.class, app, true);
    }

    @Override
    public void onConfigReloaded() {}

    @Override
    protected void initModule(CalistaGameEngine app) {
        if (soundRootNode.getParent() == null) {
            app.getRootNode().attachChild(soundRootNode);
        }
    }

    @Override
    protected void updateModule(float tpf) {}

    @Override
    protected void cleanupModule(Application app) {
        soundRootNode.depthFirstTraversal(s -> {
            if (s instanceof AudioNode audio) audio.stop();
        });
        soundRootNode.detachAllChildren();
        soundRootNode.removeFromParent();
    }

    @Override
    protected void onEnable() {}
    @Override
    protected void onDisable() {}

    /**
     * Воспроизводит 3D-звук в мире.
     */
    public void playSound(String soundPath, Vector3f position, boolean positional, float volume) {
        try {
            StereoAudioNode sound = new StereoAudioNode(getGameEngine().getAssetManager(), soundPath, AudioData.DataType.Buffer);
            sound.setPositional(positional);
            sound.setLocalTranslation(position);
            sound.setVolume(volume);
            sound.setLooping(false);
            soundRootNode.attachChild(sound);
            sound.playInstance();
        } catch (Exception e) {
            System.err.println("Failed to play sound: " + soundPath);
            e.printStackTrace();
        }
    }

    /**
     * Воспроизводит 2D-звук.
     */
    public void playSound2D(String soundPath, float volume) {
        try {
            // Для коротких эффектов лучше использовать Buffer!
            AudioNode sound = new AudioNode(getGameEngine().getAssetManager(), soundPath, AudioData.DataType.Buffer);
            sound.setPositional(false);
            sound.setVolume(volume);
            sound.setLooping(false);
            soundRootNode.attachChild(sound);
            sound.playInstance();
        } catch (Exception e) {
            System.err.println("Failed to play sound: " + soundPath);
            e.printStackTrace();
        }
    }

    /**
     * Воспроизведение фоновой музыки (Stream).
     */
    public AudioNode playMusic(String soundPath, float volume, boolean loop) {
        try {
            AudioNode sound = new AudioNode(getGameEngine().getAssetManager(), soundPath, AudioData.DataType.Stream);
            sound.setPositional(false);
            sound.setVolume(volume);
            sound.setLooping(loop);
            soundRootNode.attachChild(sound);
            sound.play();
            return sound;
        } catch (Exception e) {
            System.err.println("Failed to play music: " + soundPath);
            e.printStackTrace();
            return null;
        }
    }

    public void updateListener(Vector3f location, Quaternion rotation) {
        Listener listener = getGameEngine().getListener();
        listener.setLocation(location);
        listener.setRotation(rotation);
    }
}