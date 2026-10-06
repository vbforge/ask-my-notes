package com.vbforge.asknotes.api;

import com.vbforge.asknotes.service.RetrievalService;
import com.vbforge.asknotes.store.ScoredChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SearchControllerTest {

    @Mock
    RetrievalService retrieval;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SearchController(retrieval))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void withK_usesThatK() throws Exception {
        when(retrieval.retrieve("what is x?", 3))
                .thenReturn(List.of(new ScoredChunk("a.md", "A > B", 2, "text", 0.21)));

        mockMvc.perform(post("/search").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"what is x?\", \"k\": 3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sourceFile").value("a.md"))
                .andExpect(jsonPath("$[0].heading").value("A > B"))
                .andExpect(jsonPath("$[0].distance").value(0.21));
    }

    @Test
    void withoutK_usesTheConfiguredDefault() throws Exception {
        when(retrieval.retrieve("what is x?")).thenReturn(List.of());

        mockMvc.perform(post("/search").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"what is x?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void blankQuestion_is400() throws Exception {
        mockMvc.perform(post("/search").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("question must not be blank"));

        verifyNoInteractions(retrieval);
    }

    @Test
    void kOutOfRange_is400() throws Exception {
        mockMvc.perform(post("/search").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"x\", \"k\": 21}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("k must be between 1 and 20"));

        verifyNoInteractions(retrieval);
    }
}