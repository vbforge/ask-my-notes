package com.vbforge.asknotes.service;

import com.vbforge.asknotes.api.AskResponse;
import com.vbforge.asknotes.api.Confidence;
import com.vbforge.asknotes.ollama.OllamaChat;
import com.vbforge.asknotes.ollama.OllamaClient;
import com.vbforge.asknotes.ollama.OllamaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AskServiceTest {

    @Mock
    OllamaClient ollama;

    @Captor
    ArgumentCaptor<List<OllamaChat.Message>> messagesCaptor;

    @Captor
    ArgumentCaptor<Map<String, Object>> formatCaptor;

    AskService service;

    @BeforeEach
    void setUp() {
        service = new AskService(ollama, JsonMapper.builder().build());
    }

    @Test
    void validReply_isParsedAndTrimmed() {
        when(ollama.chat(any(), any())).thenReturn(reply("{\"answer\": \"  A virtual machine.  \", \"confidence\": \"high\"}"));

        AskResponse result = service.ask("What is the JVM?");

        assertThat(result.answer()).isEqualTo("A virtual machine.");
        assertThat(result.confidence()).isEqualTo(Confidence.HIGH);
    }

    @Test
    void sendsSystemPromptThenQuestion_withSchema() {
        when(ollama.chat(any(), any())).thenReturn(reply("{\"answer\": \"ok\", \"confidence\": \"low\"}"));

        service.ask("What is the JVM?");

        verify(ollama).chat(messagesCaptor.capture(), formatCaptor.capture());
        assertThat(messagesCaptor.getValue()).extracting(OllamaChat.Message::role)
                .containsExactly("system", "user");
        assertThat(messagesCaptor.getValue().get(1).content()).isEqualTo("What is the JVM?");
        assertThat(formatCaptor.getValue()).containsKeys("type", "properties", "required");
    }

    @Test
    void malformedJson_isBadResponse() {
        when(ollama.chat(any(), any())).thenReturn(reply("this is not json"));

        assertBadResponse();
    }

    @Test
    void blankAnswer_isBadResponse() {
        when(ollama.chat(any(), any())).thenReturn(reply("{\"answer\": \"   \", \"confidence\": \"high\"}"));

        assertBadResponse();
    }

    @Test
    void missingConfidence_isBadResponse() {
        when(ollama.chat(any(), any())).thenReturn(reply("{\"answer\": \"something\"}"));

        assertBadResponse();
    }

    @Test
    void unknownConfidenceValue_isBadResponse() {
        when(ollama.chat(any(), any())).thenReturn(reply("{\"answer\": \"something\", \"confidence\": \"certain\"}"));

        assertBadResponse();
    }

    @Test
    void clientFailure_propagatesUnchanged() {
        var failure = new OllamaException(OllamaException.Kind.UNAVAILABLE, "down");
        when(ollama.chat(any(), any())).thenThrow(failure);

        assertThatThrownBy(() -> service.ask("hi")).isSameAs(failure);
    }


    private void assertBadResponse() {
        assertThatThrownBy(() -> service.ask("hi"))
                .isInstanceOf(OllamaException.class)
                .extracting(e -> ((OllamaException) e).kind())
                .isEqualTo(OllamaException.Kind.BAD_RESPONSE);
    }

    private static OllamaChat.Response reply(String content) {
        return new OllamaChat.Response(new OllamaChat.Message("assistant", content), true, "stop", 1L);
    }

}