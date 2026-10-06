package com.vbforge.asknotes.store;

import com.vbforge.asknotes.ingest.Chunk;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Repository
public class ChunkRepository {

    private static final String INSERT = """
            INSERT INTO chunk (source_file, heading, chunk_index, content, embedding)
            VALUES (?, ?, ?, ?, CAST(? AS vector))
            """;

    private final JdbcTemplate jdbc;

    public ChunkRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Replaces all stored chunks of one file in a single transaction (re-ingestion is idempotent). */
    @Transactional
    public void replaceFile(String sourceFile, List<Chunk> chunks, List<float[]> embeddings) {
        if (chunks.size() != embeddings.size()) {
            throw new IllegalArgumentException(
                    chunks.size() + " chunks but " + embeddings.size() + " embeddings for " + sourceFile);
        }
        jdbc.update("DELETE FROM chunk WHERE source_file = ?", sourceFile);
        jdbc.batchUpdate(INSERT, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Chunk chunk = chunks.get(i);
                if (!chunk.sourceFile().equals(sourceFile)) {
                    throw new IllegalArgumentException("Chunk from " + chunk.sourceFile() + " passed for " + sourceFile);
                }
                ps.setString(1, chunk.sourceFile());
                ps.setString(2, chunk.heading());
                ps.setInt(3, chunk.index());
                ps.setString(4, chunk.content());
                ps.setString(5, toVectorLiteral(embeddings.get(i)));
            }

            @Override
            public int getBatchSize() {
                return chunks.size();
            }
        });
    }

    /** Removes chunks of notes that no longer exist on disk; returns how many files were dropped. */
    @Transactional
    public int deleteFilesNotIn(Collection<String> existingFiles) {
        Set<String> keep = Set.copyOf(existingFiles);
        List<String> stale = jdbc.queryForList("SELECT DISTINCT source_file FROM chunk", String.class)
                .stream()
                .filter(file -> !keep.contains(file))
                .toList();
        for (String file : stale) {
            jdbc.update("DELETE FROM chunk WHERE source_file = ?", file);
        }
        return stale.size();
    }

    public long count() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM chunk", Long.class);
        return n == null ? 0 : n;
    }

    /** The k chunks closest to the query vector by cosine distance, nearest first. */
    public List<ScoredChunk> findNearest(float[] queryVector, int k) {
        return jdbc.query("""
                        SELECT source_file, heading, chunk_index, content,
                               embedding <=> CAST(? AS vector) AS distance
                        FROM chunk
                        ORDER BY distance, source_file, chunk_index
                        LIMIT ?
                        """,
                (rs, rowNum) -> new ScoredChunk(
                        rs.getString("source_file"),
                        rs.getString("heading"),
                        rs.getInt("chunk_index"),
                        rs.getString("content"),
                        rs.getDouble("distance")),
                toVectorLiteral(queryVector), k);
    }

    /** pgvector's text format: [0.1,0.2,0.3]. We send it as a string and CAST in SQL. */
    static String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 10).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (!Float.isFinite(vector[i])) {
                throw new IllegalArgumentException("Embedding contains NaN or infinity at index " + i);
            }
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}