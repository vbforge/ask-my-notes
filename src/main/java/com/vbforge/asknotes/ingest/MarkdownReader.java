package com.vbforge.asknotes.ingest;

import com.vbforge.asknotes.config.NotesProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

@Component
public class MarkdownReader {

    private static final Logger log = LoggerFactory.getLogger(MarkdownReader.class);

    private final Path root;

    public MarkdownReader(NotesProperties props) {
        this.root = Path.of(props.directory()).toAbsolutePath().normalize();
    }

    public List<NoteFile> readAll() {
        if (!Files.isDirectory(root)) {
            //throw new IllegalStateException("Notes directory does not exist: " + root);
            throw new IngestionException(IngestionException.Kind.NOTES_DIRECTORY_MISSING,
                    "Notes directory does not exist: " + root);
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(MarkdownReader::isMarkdown)
                    .filter(this::isNotHidden)
                    .sorted()
                    .map(this::read)
                    .flatMap(Optional::stream)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read notes directory " + root, e);
        }
    }

    private static boolean isMarkdown(Path file) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md");
    }

    /** Skips anything inside a dot-folder or dot-file, such as .obsidian or .git. */
    private boolean isNotHidden(Path file) {
        for (Path segment : root.relativize(file)) {
            if (segment.toString().startsWith(".")) {
                return false;
            }
        }
        return true;
    }

    private Optional<NoteFile> read(Path file) {
        String relative = root.relativize(file).toString().replace('\\', '/');
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {   // BOM added by some Windows editors
                text = text.substring(1);
            }
            text = text.replace("\r\n", "\n").replace('\r', '\n');
            if (text.isBlank()) {
                log.info("Skipping empty note {}", relative);
                return Optional.empty();
            }
            return Optional.of(new NoteFile(relative, text));
        } catch (IOException e) {
            log.warn("Skipping unreadable note {}: {}", relative, e.getMessage());
            return Optional.empty();
        }
    }
}