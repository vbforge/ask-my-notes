package com.vbforge.asknotes.ingest;

import com.vbforge.asknotes.config.NotesProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MarkdownChunker {

    /** ATX heading: 1-6 '#', a space, the title, and an optional closing '#' run. */
    private static final Pattern HEADING =
            Pattern.compile("^(#{1,6})[ \\t]+(.+?)(?:[ \\t]+#+)?[ \\t]*$");

    private final int maxChars;

    @Autowired
    public MarkdownChunker(NotesProperties props) {
        this(props.chunkMaxChars());
    }

    MarkdownChunker(int maxChars) {
        if (maxChars < 200) {
            throw new IllegalArgumentException("maxChars must be at least 200");
        }
        this.maxChars = maxChars;
    }

    public List<Chunk> chunk(NoteFile note) {
        List<Chunk> chunks = new ArrayList<>();
        String[] stack = new String[7];          // current heading per level, index 1..6
        StringBuilder body = new StringBuilder();
        String path = "";
        Fence fence = new Fence();

        for (String line : stripFrontMatter(note.content()).split("\n", -1)) {
            if (!fence.consume(line)) {
                Matcher m = HEADING.matcher(line);
                if (m.matches()) {
                    emit(note.path(), path, body.toString(), chunks);
                    body.setLength(0);
                    int level = m.group(1).length();
                    stack[level] = m.group(2).strip();
                    Arrays.fill(stack, level + 1, stack.length, null);
                    path = headingPath(stack);
                    continue;
                }
            }
            body.append(line).append('\n');
        }
        emit(note.path(), path, body.toString(), chunks);
        return chunks;
    }

    private void emit(String sourceFile, String heading, String rawBody, List<Chunk> chunks) {
        String text = rawBody.strip();
        if (text.isEmpty()) {
            return;                              // heading with no text of its own
        }
        for (String piece : splitBySize(text)) {
            chunks.add(new Chunk(sourceFile, heading, chunks.size(), piece));
        }
    }

    private List<String> splitBySize(String text) {
        if (text.length() <= maxChars) {
            return List.of(text);
        }
        List<String> packed = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String block : toBlocks(text)) {
            List<String> pieces = block.length() > maxChars ? splitHard(block) : List.of(block);
            for (String piece : pieces) {
                if (current.isEmpty()) {
                    current.append(piece);
                } else if (current.length() + 2 + piece.length() <= maxChars) {
                    current.append("\n\n").append(piece);
                } else {
                    packed.add(current.toString());
                    current = new StringBuilder(piece);
                }
            }
        }
        if (!current.isEmpty()) {
            packed.add(current.toString());
        }
        return packed;
    }

    /** Paragraph-like blocks separated by blank lines; blank lines inside code fences do not split. */
    private static List<String> toBlocks(String text) {
        List<String> blocks = new ArrayList<>();
        StringBuilder block = new StringBuilder();
        Fence fence = new Fence();
        for (String line : text.split("\n", -1)) {
            boolean code = fence.consume(line);
            if (!code && line.isBlank()) {
                if (!block.isEmpty()) {
                    blocks.add(block.toString().stripTrailing());
                    block.setLength(0);
                }
            } else {
                if (!block.isEmpty()) {
                    block.append('\n');
                }
                block.append(line);
            }
        }
        if (!block.isEmpty()) {
            blocks.add(block.toString().stripTrailing());
        }
        return blocks;
    }

    /** Last resort for a single block longer than maxChars: cut at a line break, else a space, else mid-word. */
    private List<String> splitHard(String block) {
        List<String> parts = new ArrayList<>();
        String rest = block;
        while (rest.length() > maxChars) {
            int cut = rest.lastIndexOf('\n', maxChars);
            if (cut < maxChars / 2) {
                cut = Math.max(cut, rest.lastIndexOf(' ', maxChars));
            }
            if (cut <= 0) {
                cut = maxChars;
            }
            parts.add(rest.substring(0, cut).stripTrailing());
            int next = cut;
            while (next < rest.length() && (rest.charAt(next) == '\n' || rest.charAt(next) == ' ')) {
                next++;
            }
            rest = rest.substring(next);
        }
        if (!rest.isBlank()) {
            parts.add(rest);
        }
        return parts;
    }

    private static String headingPath(String[] stack) {
        List<String> parts = new ArrayList<>();
        for (int level = 1; level < stack.length; level++) {
            if (stack[level] != null) {
                parts.add(stack[level]);
            }
        }
        return String.join(" > ", parts);
    }

    private static String stripFrontMatter(String text) {
        if (text.startsWith("---\n")) {
            int end = text.indexOf("\n---\n", 3);
            if (end >= 0) {
                return text.substring(end + 5);
            }
        }
        return text;
    }

    /** Tracks whether a line belongs to a fenced code block (including the fence lines). */
    private static final class Fence {
        private String marker;                   // null when outside a fence

        boolean consume(String line) {
            String t = line.stripLeading();
            if (marker != null) {
                if (t.startsWith(marker)) {
                    marker = null;
                }
                return true;
            }
            if (t.startsWith("```") || t.startsWith("~~~")) {
                marker = t.substring(0, 3);
                return true;
            }
            return false;
        }
    }
}