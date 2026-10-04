package com.vbforge.asknotes.ingest;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownChunkerTest {

    private final MarkdownChunker chunker = new MarkdownChunker(200);

    private List<Chunk> chunk(String markdown) {
        return chunker.chunk(new NoteFile("java/notes.md", markdown));
    }

    @Test
    void headingPathsFollowTheHierarchy() {
        List<Chunk> chunks = chunk("""
                # Java
                intro text
                ## Streams
                stream text
                ### Collectors
                collector text
                ## Records
                record text
                """);

        assertThat(chunks).extracting(Chunk::heading).containsExactly(
                "Java", "Java > Streams", "Java > Streams > Collectors", "Java > Records");
        assertThat(chunks).extracting(Chunk::content).containsExactly(
                "intro text", "stream text", "collector text", "record text");
        assertThat(chunks).extracting(Chunk::sourceFile).containsOnly("java/notes.md");
    }

    @Test
    void textBeforeTheFirstHeadingHasAnEmptyHeadingPath() {
        List<Chunk> chunks = chunk("preface line\n# A\nbody\n");

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).heading()).isEmpty();
        assertThat(chunks.get(0).content()).isEqualTo("preface line");
    }

    @Test
    void headingWithoutOwnTextProducesNoChunkButStaysInThePath() {
        List<Chunk> chunks = chunk("# A\n## B\nbody\n");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).heading()).isEqualTo("A > B");
    }

    @Test
    void hashInsideCodeFenceIsNotAHeading() {
        List<Chunk> chunks = chunk("""
                # Title
                some text

                ```bash
                # not a heading

                echo hi
                ```
                """);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).heading()).isEqualTo("Title");
        assertThat(chunks.get(0).content()).contains("# not a heading", "echo hi");
    }

    @Test
    void frontMatterIsSkipped() {
        List<Chunk> chunks = chunk("---\ntags: [java]\n---\n# H\nbody\n");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).content()).isEqualTo("body");
    }

    @Test
    void longSectionIsPackedByParagraphsWithinTheLimit() {
        String paragraph = "word ".repeat(20).strip();            // 99 characters
        String markdown = "# Long\n" + String.join("\n\n", java.util.Collections.nCopies(5, paragraph));

        List<Chunk> chunks = chunk(markdown);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(c -> assertThat(c.content().length()).isLessThanOrEqualTo(200));
        assertThat(countWords(chunks)).isEqualTo(100);            // nothing lost
    }

    @Test
    void oversizedSingleParagraphIsCutAtWordBoundaries() {
        List<Chunk> chunks = chunk("# Wall\n" + "word ".repeat(90).strip());

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(c -> assertThat(c.content().length()).isLessThanOrEqualTo(200));
        assertThat(countWords(chunks)).isEqualTo(90);
    }

    @Test
    void chunkIndexesAreSequentialPerFile() {
        List<Chunk> chunks = chunk("# A\none\n# B\ntwo\n# C\nthree\n");

        assertThat(chunks).extracting(Chunk::index)
                .containsExactlyElementsOf(IntStream.range(0, chunks.size()).boxed().toList());
    }

    @Test
    void embeddingTextPrependsTheHeadingPath() {
        Chunk withHeading = new Chunk("a.md", "Java > Streams", 0, "text");
        Chunk withoutHeading = new Chunk("a.md", "", 1, "text");

        assertThat(withHeading.embeddingText()).isEqualTo("Java > Streams\n\ntext");
        assertThat(withoutHeading.embeddingText()).isEqualTo("text");
    }

    private static long countWords(List<Chunk> chunks) {
        return chunks.stream()
                .flatMap(c -> java.util.Arrays.stream(c.content().split("\\s+")))
                .filter(w -> w.equals("word"))
                .count();
    }

}