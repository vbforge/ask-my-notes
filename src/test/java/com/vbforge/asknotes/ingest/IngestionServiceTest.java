package com.vbforge.asknotes.ingest;

import com.vbforge.asknotes.ollama.OllamaClient;
import com.vbforge.asknotes.ollama.OllamaException;
import com.vbforge.asknotes.store.ChunkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    @Mock
    MarkdownReader reader;

    @Mock
    OllamaClient ollama;

    @Mock
    ChunkRepository repository;

    IngestionService service;

    @BeforeEach
    void setUp() {
        service = new IngestionService(reader, new MarkdownChunker(200), ollama, repository);
    }

    @Test
    void embedsInBatchesOf16_storesPerFile_andCleansUpStaleFiles() {
        when(reader.readAll()).thenReturn(List.of(note("big.md", 40), note("small.md", 3)));
        when(ollama.embedDocuments(anyList())).thenAnswer(inv ->
                inv.<List<String>>getArgument(0).stream().map(text -> new float[3]).toList());

        IngestionReport report = service.ingest();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> batches = ArgumentCaptor.forClass(List.class);
        verify(ollama, times(4)).embedDocuments(batches.capture());
        assertThat(batches.getAllValues()).extracting(List::size).containsExactly(16, 16, 8, 3);

        verify(repository).replaceFile(org.mockito.ArgumentMatchers.eq("big.md"), anyList(), anyList());
        verify(repository).replaceFile(org.mockito.ArgumentMatchers.eq("small.md"), anyList(), anyList());
        verify(repository).deleteFilesNotIn(List.of("big.md", "small.md"));
        assertThat(report.files()).isEqualTo(2);
        assertThat(report.chunks()).isEqualTo(43);
    }

    @Test
    void ollamaFailure_abortsBeforeTouchingTheDatabase() {
        when(reader.readAll()).thenReturn(List.of(note("a.md", 2)));
        var failure = new OllamaException(OllamaException.Kind.UNAVAILABLE, "down");
        when(ollama.embedDocuments(anyList())).thenThrow(failure);

        assertThatThrownBy(() -> service.ingest()).isSameAs(failure);

        verifyNoInteractions(repository);
    }

    /** A note with n tiny sections, which the chunker turns into n chunks. */
    private static NoteFile note(String path, int sections) {
        StringBuilder md = new StringBuilder();
        for (int i = 0; i < sections; i++) {
            md.append("# H").append(i).append("\ntext ").append(i).append("\n");
        }
        return new NoteFile(path, md.toString());
    }
}