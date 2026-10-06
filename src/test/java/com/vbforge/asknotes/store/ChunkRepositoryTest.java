package com.vbforge.asknotes.store;

import com.vbforge.asknotes.TestcontainersConfiguration;
import com.vbforge.asknotes.ingest.Chunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ChunkRepositoryTest {

    private static final int DIMS = 768;

    @Autowired
    ChunkRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM chunk");
    }

    @Test
    void replaceFile_storesChunksWithEmbeddings() {
        repository.replaceFile("a.md",
                List.of(chunk("a.md", "Java > Streams", 0, "stream text"), chunk("a.md", "", 1, "other")),
                List.of(unit(0), unit(1)));

        assertThat(repository.count()).isEqualTo(2);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT heading, content, vector_dims(embedding) AS dims FROM chunk WHERE chunk_index = 0");
        assertThat(row).containsEntry("heading", "Java > Streams")
                .containsEntry("content", "stream text")
                .containsEntry("dims", DIMS);
    }

    @Test
    void replaceFile_isIdempotent() {
        repository.replaceFile("a.md", List.of(chunk("a.md", "", 0, "old")), List.of(unit(0)));
        repository.replaceFile("a.md",
                List.of(chunk("a.md", "", 0, "new one"), chunk("a.md", "", 1, "new two")),
                List.of(unit(1), unit(2)));

        assertThat(contents("a.md")).containsExactly("new one", "new two");
    }

    @Test
    void replaceFile_leavesOtherFilesAlone() {
        repository.replaceFile("a.md", List.of(chunk("a.md", "", 0, "a-old")), List.of(unit(0)));
        repository.replaceFile("b.md", List.of(chunk("b.md", "", 0, "b-text")), List.of(unit(1)));

        repository.replaceFile("a.md", List.of(chunk("a.md", "", 0, "a-new")), List.of(unit(2)));

        assertThat(contents("a.md")).containsExactly("a-new");
        assertThat(contents("b.md")).containsExactly("b-text");
    }

    @Test
    void deleteFilesNotIn_removesChunksOfDeletedNotes() {
        repository.replaceFile("a.md", List.of(chunk("a.md", "", 0, "a")), List.of(unit(0)));
        repository.replaceFile("b.md", List.of(chunk("b.md", "", 0, "b")), List.of(unit(1)));
        repository.replaceFile("c.md", List.of(chunk("c.md", "", 0, "c")), List.of(unit(2)));

        int removed = repository.deleteFilesNotIn(List.of("a.md"));

        assertThat(removed).isEqualTo(2);
        assertThat(contents("a.md")).containsExactly("a");
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void nearestNeighborQuery_returnsTheClosestChunk() {
        repository.replaceFile("a.md",
                List.of(chunk("a.md", "", 0, "alpha"), chunk("a.md", "", 1, "beta"), chunk("a.md", "", 2, "gamma")),
                List.of(unit(10), unit(20), unit(30)));

        String nearest = jdbc.queryForObject(
                "SELECT content FROM chunk ORDER BY embedding <=> CAST(? AS vector) LIMIT 1",
                String.class, ChunkRepository.toVectorLiteral(unit(20)));

        assertThat(nearest).isEqualTo("beta");
    }

    @Test
    void wrongDimensions_failAndKeepTheOldChunks() {
        repository.replaceFile("a.md", List.of(chunk("a.md", "", 0, "keep me")), List.of(unit(0)));

        assertThatThrownBy(() -> repository.replaceFile("a.md",
                List.of(chunk("a.md", "", 0, "bad")), List.of(new float[]{1f, 2f, 3f})))
                .isInstanceOf(DataAccessException.class);

        assertThat(contents("a.md")).containsExactly("keep me");   // the transaction rolled back the DELETE
    }

    @Test
    void chunkAndEmbeddingCountMismatch_isRejected() {
        assertThatThrownBy(() -> repository.replaceFile("a.md",
                List.of(chunk("a.md", "", 0, "x")), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void vectorLiteral_usesPgvectorTextFormat() {
        assertThat(ChunkRepository.toVectorLiteral(new float[]{0.5f, 1f, -2.25f})).isEqualTo("[0.5,1.0,-2.25]");
    }

    @Test
    void vectorLiteral_rejectsNaN() {
        assertThatThrownBy(() -> ChunkRepository.toVectorLiteral(new float[]{1f, Float.NaN}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findNearest_ordersByCosineDistance_andHonorsK() {
        repository.replaceFile("a.md",
                List.of(chunk("a.md", "", 0, "alpha"), chunk("a.md", "", 1, "beta"), chunk("a.md", "", 2, "gamma")),
                List.of(unit(10), unit(20), unit(30)));

        float[] query = unit(20);
        query[10] = 0.2f;        // mostly "beta", slightly "alpha", nothing of "gamma"

        List<ScoredChunk> hits = repository.findNearest(query, 3);

        assertThat(hits).extracting(ScoredChunk::content).containsExactly("beta", "alpha", "gamma");
        assertThat(hits.get(0).distance()).isCloseTo(0.0194, org.assertj.core.api.Assertions.within(0.001));
        assertThat(hits.get(1).distance()).isCloseTo(0.804, org.assertj.core.api.Assertions.within(0.001));
        assertThat(hits.get(2).distance()).isCloseTo(1.0, org.assertj.core.api.Assertions.within(0.001));
        assertThat(repository.findNearest(query, 2)).hasSize(2);
    }

    @Test
    void findNearest_onEmptyTable_returnsNothing() {
        assertThat(repository.findNearest(unit(0), 4)).isEmpty();
    }

    private List<String> contents(String file) {
        return jdbc.queryForList(
                "SELECT content FROM chunk WHERE source_file = ? ORDER BY chunk_index", String.class, file);
    }

    private static Chunk chunk(String file, String heading, int index, String content) {
        return new Chunk(file, heading, index, content);
    }

    /** A 768-dimension vector with a single 1.0 at the given position. */
    private static float[] unit(int hot) {
        float[] v = new float[DIMS];
        v[hot] = 1f;
        return v;
    }
}