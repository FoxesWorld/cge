package org.foxesworld.cge.modules.sound;

import com.jme3.asset.AssetManager;
import com.jme3.audio.AudioContext;
import com.jme3.audio.AudioData.DataType;
import com.jme3.audio.AudioNode;
import com.jme3.audio.AudioParam;
import com.jme3.audio.AudioRenderer;
import com.jme3.math.Vector3f;
import com.jme3.export.OutputCapsule;
import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;

import java.io.IOException;

/**
 * StereoAudioNode extends AudioNode to support 3D positional stereo playback.
 * <p>
 * Features:
 * <ul>
 *   <li>Playback of stereo audio files (2 channels) with basic panning calculation.
 *   <li>Playback of stereo audio files (2 channels) with basic panning calculation.
 *   <li>Inverse-distance attenuation based on reference and max distances.
 *   <li>Configurable listener reference for dynamic panning.</li>
 * </ul>
 * <p>
 * Note: Default OpenAL and jME3 AudioRenderer do not natively support 3D stereo sources.
 * A custom mixer or audio engine is required for true stereo positioning.
 */
public class StereoAudioNode extends AudioNode {

    private static final float DEFAULT_MAX_DISTANCE = 50f;
    private static final float DEFAULT_REF_DISTANCE = 5f;

    private float pan = 0f;
    private float maxDistance = DEFAULT_MAX_DISTANCE;
    private float refDistance = DEFAULT_REF_DISTANCE;
    private com.jme3.audio.Listener listener;

    /** Default constructor for serialization. */
    public StereoAudioNode() {
        super();
    }

    /**
     * Constructs a StereoAudioNode with the given stereo file.
     *
     * @param assetManager the AssetManager to load audio data
     * @param name         the path to the stereo file
     * @param type         STREAM for music or BUFFER for effects
     */
    public StereoAudioNode(AssetManager assetManager, String name, DataType type) {
        super(assetManager, name, type);
        if (getAudioData().getChannels() != 2) {
            throw new IllegalArgumentException(
                    "StereoAudioNode requires a 2-channel audio file: " + name);
        }
    }

    /**
     * Sets the listener for panning calculations.
     *
     * @param listener the audio listener
     */
    public void setListener(com.jme3.audio.Listener listener) {
        this.listener = listener;
    }

    @Override
    public void play() {
        ensureDataLoaded();
        updatePanAndAttenuation();
        // Bypass mono check in super: call renderer directly
        AudioContext.getAudioRenderer().playSource(this);
    }
    @Override
    public void playInstance() {
        ensureDataLoaded();
        updatePanAndAttenuation();
        // Bypass mono-only check in super
        getRenderer().playSourceInstance(this);
    }

    /**
     * Updates pan ([-1..1]) and volume attenuation before playback.
     */
    protected void updatePanAndAttenuation() {
        if (!isPositional() || listener == null) {
            pan = 0f;
        } else {
            Vector3f src = getWorldTranslation();
            Vector3f lst = listener.getLocation();
            Vector3f diff = src.subtract(lst);
            float distance = diff.length();

            // Panning: project normalized direction onto listener's left axis
            Vector3f leftAxis = listener.getRotation().mult(Vector3f.UNIT_X);
            pan = clamp(leftAxis.dot(diff.normalizeLocal()), -1f, 1f);

            // Distance attenuation: inverse distance model
            float attenuation = refDistance / (refDistance + Math.max(distance - refDistance, 0f));
            setVolume(clamp(attenuation, 0f, 1f));
        }
        // Apply pan parameter to renderer if active
        int ch = getChannel();
        if (ch >= 0) {
            AudioRenderer renderer = AudioContext.getAudioRenderer();
            renderer.updateSourceParam(this, AudioParam.Direction);
            renderer.updateSourceParam(this, AudioParam.Volume);
        }
    }

    private void ensureDataLoaded() {
        if (getAudioData() == null) {
            throw new IllegalStateException("Audio data not loaded: " + getName());
        }
    }

    private float clamp(float v, float min, float max) {
        return (v < min) ? min : (v > max) ? max : v;
    }

    /**
     * Gets the current pan value.
     *
     * @return pan in range [-1,1]
     */
    public float getPanValue() {
        return pan;
    }

    /**
     * Sets maximum distance for attenuation.
     */
    public void setMaxDistance(float maxDistance) {
        this.maxDistance = maxDistance;
    }

    /**
     * Sets reference distance for attenuation model.
     */
    public void setRefDistance(float refDistance) {
        this.refDistance = refDistance;
    }

    @Override
    public StereoAudioNode clone() {
        StereoAudioNode c = (StereoAudioNode) super.clone();
        c.pan = pan;
        c.maxDistance = maxDistance;
        c.refDistance = refDistance;
        c.listener = listener;
        return c;
    }

    @Override
    public void read(JmeImporter im) throws IOException {
        super.read(im);
        InputCapsule ic = im.getCapsule(this);
        maxDistance = ic.readFloat("maxDistance", DEFAULT_MAX_DISTANCE);
        refDistance = ic.readFloat("refDistance", DEFAULT_REF_DISTANCE);
    }

    @Override
    public void write(JmeExporter ex) throws IOException {
        super.write(ex);
        OutputCapsule oc = ex.getCapsule(this);
        oc.write(maxDistance, "maxDistance", DEFAULT_MAX_DISTANCE);
        oc.write(refDistance, "refDistance", DEFAULT_REF_DISTANCE);
    }
}
