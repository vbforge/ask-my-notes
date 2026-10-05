package com.vbforge.asknotes.ingest;

import com.vbforge.asknotes.ollama.OllamaClient;
import com.vbforge.asknotes.store.ChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    /** Chunks per embedding request: few round trips, yet each stays well inside the read timeout. */
    private static final int EMBED_BATCH_SIZE = 16;

    private final MarkdownReader reader;
    private final MarkdownChunker chunker;
    private final OllamaClient ollama;
    private final ChunkRepository repository;
    private final ReentrantLock running = new ReentrantLock();

    public IngestionService(MarkdownReader reader, MarkdownChunker chunker,
                            OllamaClient ollama, ChunkRepository repository) {
        this.reader = reader;
        this.chunker = chunker;
        this.ollama = ollama;
        this.repository = repository;
    }

    public IngestionReport ingest() {
        if (!running.tryLock()) {
            throw new IngestionException(IngestionException.Kind.ALREADY_RUNNING,
                    "An ingestion is already running");
        }
        try {
            return doIngest();
        } finally {
            running.unlock();
        }
    }

    private IngestionReport doIngest() {
        long started = System.nanoTime();
        List<NoteFile> notes = reader.readAll();

        int chunkTotal = 0;
        for (NoteFile note : notes) {
            List<Chunk> chunks = chunker.chunk(note);
            List<float[]> vectors = embedInBatches(chunks);
            repository.replaceFile(note.path(), chunks, vectors);   // one transaction per file
            chunkTotal += chunks.size();
            log.info("Ingested {} ({} chunks)", note.path(), chunks.size());
        }

        int removed = repository.deleteFilesNotIn(notes.stream().map(NoteFile::path).toList());
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        log.info("Ingestion finished: {} files, {} chunks, {} files removed, {} ms",
                notes.size(), chunkTotal, removed, millis);
        return new IngestionReport(notes.size(), chunkTotal, removed, millis);
    }

    private List<float[]> embedInBatches(List<Chunk> chunks) {
        List<float[]> vectors = new ArrayList<>(chunks.size());
        for (int from = 0; from < chunks.size(); from += EMBED_BATCH_SIZE) {
            int to = Math.min(from + EMBED_BATCH_SIZE, chunks.size());
            List<String> texts = chunks.subList(from, to).stream().map(Chunk::embeddingText).toList();
            vectors.addAll(ollama.embedDocuments(texts));
        }
        return vectors;
    }
}