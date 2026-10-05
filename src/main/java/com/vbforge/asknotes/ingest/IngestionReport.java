package com.vbforge.asknotes.ingest;

public record IngestionReport(int files, int chunks, int removedFiles, long durationMs) {
}