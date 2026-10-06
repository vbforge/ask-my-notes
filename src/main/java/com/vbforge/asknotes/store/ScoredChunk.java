package com.vbforge.asknotes.store;

/**
 * A stored chunk together with its cosine distance to a query (0 = same direction, 2 = opposite).
 */
public record ScoredChunk(
        String sourceFile,
        String heading,
        int chunkIndex,
        String content,
        double distance
) {
}