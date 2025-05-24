package org.foxesworld.cge.core.cgs;

public record CGSMetadata(
        String magic,
        String sceneName,
        int version,
        long tableOffset,
        int chunkCount
) {}