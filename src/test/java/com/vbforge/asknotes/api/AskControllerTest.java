package com.vbforge.asknotes.api;

import com.vbforge.asknotes.ollama.OllamaException;
import com.vbforge.asknotes.service.AskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AskControllerTest {

    @Mock
    AskService askService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AskController(askService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void validQuestion_returnsAnswer() throws Exception {
        when(askService.ask("What is the JVM?"))
                .thenReturn(new AskResponse("A virtual machine.", Confidence.HIGH));

        mockMvc.perform(post("/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"What is the JVM?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("A virtual machine."))
                .andExpect(jsonPath("$.confidence").value("high"));
    }

    @Test
    void blankQuestion_is400_andServiceNotCalled() throws Exception {
        mockMvc.perform(post("/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("question must not be blank"));

        verifyNoInteractions(askService);
    }

    @Test
    void tooLongQuestion_is400() throws Exception {
        String body = "{\"question\": \"" + "a".repeat(2001) + "\"}";

        mockMvc.perform(post("/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("question must be at most 2000 characters"));

        verifyNoInteractions(askService);
    }

    @Test
    void malformedBody_is400() throws Exception {
        mockMvc.perform(post("/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("hello"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").exists());

        verifyNoInteractions(askService);
    }

    @ParameterizedTest
    @CsvSource({
            "UNAVAILABLE, 503",
            "TIMEOUT, 504",
            "BAD_RESPONSE, 502"
    })
    void ollamaFailures_mapToStatusCodes(OllamaException.Kind kind, int expectedStatus) throws Exception {
        when(askService.ask(anyString())).thenThrow(new OllamaException(kind, "boom"));

        mockMvc.perform(post("/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"hi\"}"))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.detail").value("boom"));
    }

}