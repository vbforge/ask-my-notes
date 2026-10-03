package com.vbforge.asknotes.ingest;

/**
 * One Markdown file.
 *
 * @param path    path relative to the notes folder, always with '/' separators (this is what we cite)
 * @param content full text with normalized line endings
 */
public record NoteFile(String path, String content) {
}