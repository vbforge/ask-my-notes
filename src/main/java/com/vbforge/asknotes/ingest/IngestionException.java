package com.vbforge.asknotes.ingest;

public class IngestionException extends RuntimeException {

    public enum Kind {
        /** Another ingestion is already running. */
        ALREADY_RUNNING,
        /** The configured notes folder does not exist. */
        NOTES_DIRECTORY_MISSING
    }

    private final Kind kind;

    public IngestionException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}