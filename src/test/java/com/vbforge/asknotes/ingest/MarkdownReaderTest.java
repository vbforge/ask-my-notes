package com.vbforge.asknotes.ingest;

import com.vbforge.asknotes.config.NotesProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class MarkdownReaderTest {

    @TempDir
    Path notes;

    @Test
    void readsMarkdownRecursively_withRelativeSlashPaths() throws IOException {
        write("a.md", "# A\none");
        write("sub/deep/b.MD", "# B\ntwo");
        write("ignore.txt", "not markdown");

        assertThat(reader().readAll()).extracting(NoteFile::path)
                .containsExactly("a.md", "sub/deep/b.MD");
    }

    @Test
    void skipsHiddenFoldersAndFiles() throws IOException {
        write(".obsidian/config.md", "# settings");
        write(".hidden.md", "# hidden");
        write("ok.md", "# visible");

        assertThat(reader().readAll()).extracting(NoteFile::path).containsExactly("ok.md");
    }

    @Test
    void skipsEmptyNotes() throws IOException {
        write("empty.md", "  \n\n");
        write("full.md", "# T\ntext");

        assertThat(reader().readAll()).extracting(NoteFile::path).containsExactly("full.md");
    }

    @Test
    void stripsBomAndNormalizesLineEndings() throws IOException {
        write("win.md", "\uFEFF# T\r\nline1\r\nline2\r");

        assertThat(reader().readAll()).singleElement()
                .extracting(NoteFile::content).isEqualTo("# T\nline1\nline2\n");
    }

    @Test
    void missingDirectory_isAnIngestionError() {
        MarkdownReader missing = new MarkdownReader(new NotesProperties(notes.resolve("nope").toString(), 1200));

        assertThatExceptionOfType(IngestionException.class)
                .isThrownBy(missing::readAll)
                .satisfies(e -> assertThat(e.kind()).isEqualTo(IngestionException.Kind.NOTES_DIRECTORY_MISSING));
    }

    private MarkdownReader reader() {
        return new MarkdownReader(new NotesProperties(notes.toString(), 1200));
    }

    private void write(String relative, String content) throws IOException {
        Path file = notes.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}