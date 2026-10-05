package com.vbforge.asknotes.api;

import com.vbforge.asknotes.ingest.IngestionReport;
import com.vbforge.asknotes.ingest.IngestionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IngestController {

    private final IngestionService ingestionService;

    public IngestController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/ingest")
    public IngestionReport ingest() {
        return ingestionService.ingest();
    }
}