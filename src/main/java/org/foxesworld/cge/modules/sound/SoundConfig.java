package org.foxesworld.cge.modules.sound;

import org.foxesworld.cge.core.module.ModuleConfig;

public class SoundConfig extends ModuleConfig {
    private int maxConvolutionSources = 8;
    private int numDiffuseSamples = 64;
    private int numRays = 2048;
    private int numOcclusionSamples = 128;
    private int sampleRate = 44100;
    private int frameSize  = 1024;

    public int getMaxConvolutionSources() {
        return maxConvolutionSources;
    }

    public int getNumDiffuseSamples() {
        return numDiffuseSamples;
    }

    public int getNumRays() {
        return numRays;
    }

    public int getNumOcclusionSamples() {
        return numOcclusionSamples;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public int getFrameSize() {
        return frameSize;
    }
}
