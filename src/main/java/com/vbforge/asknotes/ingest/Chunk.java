package com.vbforge.asknotes.ingest;

/**
 * @param sourceFile relative path of the note (what we cite)
 * @param heading    heading path such as "Java > Streams", or "" before the first heading
 * @param index      position of the chunk within its file, starting at 0
 * @param content    the chunk text, as shown to the user
 */
public record Chunk(String sourceFile, String heading, int index, String content) {

    /** Text sent to the embedding model: the heading path gives a short chunk its context. */
    public String embeddingText() {
        return heading.isEmpty() ? content : heading + "\n\n" + content;
    }
}