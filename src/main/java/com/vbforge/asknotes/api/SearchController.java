package com.vbforge.asknotes.api;

import com.vbforge.asknotes.service.RetrievalService;
import com.vbforge.asknotes.store.ScoredChunk;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Diagnostic endpoint: shows what retrieval finds, without asking the chat model anything. */
@RestController
public class SearchController {

    private final RetrievalService retrieval;

    public SearchController(RetrievalService retrieval) {
        this.retrieval = retrieval;
    }

    @PostMapping("/search")
    public List<ScoredChunk> search(@Valid @RequestBody SearchRequest request) {
        return request.k() == null
                ? retrieval.retrieve(request.question())
                : retrieval.retrieve(request.question(), request.k());
    }
}