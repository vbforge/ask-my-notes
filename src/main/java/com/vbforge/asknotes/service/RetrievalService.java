package com.vbforge.asknotes.service;

import com.vbforge.asknotes.config.RetrievalProperties;
import com.vbforge.asknotes.ollama.OllamaClient;
import com.vbforge.asknotes.store.ChunkRepository;
import com.vbforge.asknotes.store.ScoredChunk;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RetrievalService {

    private final OllamaClient ollama;
    private final ChunkRepository repository;
    private final RetrievalProperties props;

    public RetrievalService(OllamaClient ollama, ChunkRepository repository, RetrievalProperties props) {
        this.ollama = ollama;
        this.repository = repository;
        this.props = props;
    }

    public List<ScoredChunk> retrieve(String question) {
        return retrieve(question, props.topK());
    }

    public List<ScoredChunk> retrieve(String question, int k) {
        float[] queryVector = ollama.embedQuery(question);   // adds the "search_query: " prefix
        return repository.findNearest(queryVector, k);
    }
}