package com.vbforge.asknotes.service;

import com.vbforge.asknotes.api.AskResponse;
import com.vbforge.asknotes.api.Confidence;
import com.vbforge.asknotes.config.RetrievalProperties;
import com.vbforge.asknotes.ollama.OllamaChat;
import com.vbforge.asknotes.ollama.OllamaClient;
import com.vbforge.asknotes.ollama.OllamaException;
import com.vbforge.asknotes.store.ScoredChunk;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AskServiceTest {

    private static final double MAX_DISTANCE = 0.33;
    private static final String OK_REPLY = "{\"answer\": \"ok\", \"confidence\": \"low\"}";

    @Mock
    OllamaClient ollama;

    @Mock
    RetrievalService retrieval;

    @Captor
    ArgumentCaptor<List<OllamaChat.Message>> messagesCaptor;

    @Captor
    ArgumentCaptor<Map<String, Object>> formatCaptor;

    AskService service;

    @BeforeEach
    void setUp() {
        service = new AskService(ollama, retrieval,
                new RetrievalProperties(4, MAX_DISTANCE), JsonMapper.builder().build());
    }

    // ---- distance gate ----

    @Test
    void noChunkWithinThreshold_returnsNotFound_withoutCallingChat() {
        when(retrieval.retrieve("capital of France?"))
                .thenReturn(List.of(chunk("a.md", "A", "text", 0.49), chunk("b.md", "B", "text", 0.55)));

        AskResponse result = service.ask("capital of France?");

        assertThat(result.answer()).isEqualTo(AskService.NOT_FOUND_ANSWER);
        assertThat(result.confidence()).isEqualTo(Confidence.LOW);
        assertThat(result.sources()).isEmpty();
        verifyNoInteractions(ollama);
    }

    @Test
    void emptyRetrieval_returnsNotFound_withoutCallingChat() {
        when(retrieval.retrieve("anything")).thenReturn(List.of());

        AskResponse result = service.ask("anything");

        assertThat(result.answer()).isEqualTo(AskService.NOT_FOUND_ANSWER);
        assertThat(result.sources()).isEmpty();
        verifyNoInteractions(ollama);
    }

    @Test
    void selectRelevant_keepsChunksAtOrBelowThreshold() {
        List<ScoredChunk> chunks = List.of(
                chunk("a.md", "", "1", 0.20),
                chunk("b.md", "", "2", MAX_DISTANCE),   // exactly at the threshold is kept
                chunk("c.md", "", "3", 0.331));

        assertThat(AskService.selectRelevant(chunks, MAX_DISTANCE))
                .extracting(ScoredChunk::sourceFile)
                .containsExactly("a.md", "b.md");
    }

    // ---- answer path ----

    @Test
    void relevantChunks_answerIsParsedTrimmed_andSourcesAttached() {
        when(retrieval.retrieve("What database?"))
                .thenReturn(List.of(chunk("README-1.md", "Fewster > Stack", "Uses PostgreSQL.", 0.20)));
        when(ollama.chat(any(), any()))
                .thenReturn(reply("{\"answer\": \"  PostgreSQL.  \", \"confidence\": \"high\"}"));

        AskResponse result = service.ask("What database?");

        assertThat(result.answer()).isEqualTo("PostgreSQL.");
        assertThat(result.confidence()).isEqualTo(Confidence.HIGH);
        assertThat(result.sources()).containsExactly(new AskResponse.Source("README-1.md", "Fewster > Stack"));
    }

    @Test
    void prompt_hasSystemThenUserMessage_withSchema_andOnlyRelevantChunks() {
        when(retrieval.retrieve("What database?")).thenReturn(List.of(
                chunk("README-1.md", "Stack", "Uses PostgreSQL.", 0.20),
                chunk("README-2.md", "Other", "Unrelated text.", 0.60)));
        when(ollama.chat(any(), any())).thenReturn(reply(OK_REPLY));

        service.ask("What database?");

        verify(ollama).chat(messagesCaptor.capture(), formatCaptor.capture());
        List<OllamaChat.Message> messages = messagesCaptor.getValue();
        assertThat(messages).extracting(OllamaChat.Message::role).containsExactly("system", "user");
        assertThat(messages.get(1).content())
                .contains("[1] README-1.md > Stack")
                .contains("Uses PostgreSQL.")
                .contains("Question: What database?")
                .doesNotContain("Unrelated text.");
        assertThat(formatCaptor.getValue()).containsKeys("type", "properties", "required");
    }

    @Test
    void sources_onlyIncludeRelevantChunks() {
        when(retrieval.retrieve("q")).thenReturn(List.of(
                chunk("a.md", "A", "x", 0.10),
                chunk("b.md", "B", "y", 0.80)));
        when(ollama.chat(any(), any())).thenReturn(reply(OK_REPLY));

        AskResponse result = service.ask("q");

        assertThat(result.sources()).extracting(AskResponse.Source::file).containsExactly("a.md");
    }

    // ---- prompt building / sources helpers ----

    @Test
    void buildUserMessage_numbersChunks_andOmitsBlankHeading() {
        String message = AskService.buildUserMessage("Why?", List.of(
                chunk("a.md", "A > B", "first", 0.1),
                chunk("b.md", "", "second", 0.2)));

        assertThat(message)
                .startsWith("Context:")
                .contains("[1] a.md > A > B\nfirst")
                .contains("[2] b.md\nsecond")
                .endsWith("Question: Why?");
    }

    @Test
    void toSources_deduplicatesByFileAndHeading_keepingRelevanceOrder() {
        List<AskResponse.Source> sources = AskService.toSources(List.of(
                chunk("a.md", "Intro", "1", 0.10),
                chunk("b.md", "Intro", "2", 0.15),
                chunk("a.md", "Intro", "3", 0.20),     // duplicate of the first
                chunk("a.md", "Usage", "4", 0.25)));   // same file, different heading

        assertThat(sources).containsExactly(
                new AskResponse.Source("a.md", "Intro"),
                new AskResponse.Source("b.md", "Intro"),
                new AskResponse.Source("a.md", "Usage"));
    }

    // ---- bad model output ----

    @Test
    void malformedJson_isBadResponse() {
        assertBadResponse("this is not json");
    }

    @Test
    void blankAnswer_isBadResponse() {
        assertBadResponse("{\"answer\": \"   \", \"confidence\": \"high\"}");
    }

    @Test
    void missingConfidence_isBadResponse() {
        assertBadResponse("{\"answer\": \"something\"}");
    }

    @Test
    void unknownConfidenceValue_isBadResponse() {
        assertBadResponse("{\"answer\": \"something\", \"confidence\": \"certain\"}");
    }

    // ---- failures from dependencies ----

    @Test
    void chatFailure_propagatesUnchanged() {
        var failure = new OllamaException(OllamaException.Kind.UNAVAILABLE, "down");
        when(retrieval.retrieve("hi")).thenReturn(List.of(chunk("a.md", "A", "x", 0.10)));
        when(ollama.chat(any(), any())).thenThrow(failure);

        assertThatThrownBy(() -> service.ask("hi")).isSameAs(failure);
    }

    @Test
    void retrievalFailure_propagatesUnchanged_andChatIsNotCalled() {
        var failure = new OllamaException(OllamaException.Kind.TIMEOUT, "embedding timed out");
        when(retrieval.retrieve("hi")).thenThrow(failure);

        assertThatThrownBy(() -> service.ask("hi")).isSameAs(failure);
        verifyNoInteractions(ollama);
    }

    // ---- helpers ----

    private void assertBadResponse(String modelContent) {
        when(retrieval.retrieve("hi")).thenReturn(List.of(chunk("a.md", "A", "x", 0.10)));
        when(ollama.chat(any(), any())).thenReturn(reply(modelContent));

        assertThatThrownBy(() -> service.ask("hi"))
                .isInstanceOf(OllamaException.class)
                .extracting(e -> ((OllamaException) e).kind())
                .isEqualTo(OllamaException.Kind.BAD_RESPONSE);
    }

    private static ScoredChunk chunk(String file, String heading, String content, double distance) {
        return new ScoredChunk(file, heading, 0, content, distance);
    }

    private static OllamaChat.Response reply(String content) {
        return new OllamaChat.Response(new OllamaChat.Message("assistant", content), true, "stop", 1L);
    }
}