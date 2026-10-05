package com.vbforge.asknotes.ollama;

import com.vbforge.asknotes.config.OllamaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Map;


/**
 * *Why it looks like this:**
 * - **`stream: false`** returns one complete JSON object, which is what your curl tests used. Streaming is a nicer UX but belongs outside Stage 1.
 * - **`temperature: 0`** makes answers repeatable, which matters for the Stage 4 evaluation tests.
 * - **Connection failures and timeouts are distinguished** by walking the cause chain for `HttpTimeoutException`.
 * A refused connection (container stopped) becomes `UNAVAILABLE`, and a slow generation becomes `TIMEOUT`.
 * - **A 404 gets a hint**, because "model not pulled" is the most likely real-world cause of a 404 from Ollama.
 * - **A response cut off by the token limit is rejected**, since truncated structured output would be invalid JSON anyway.
 */


/**
 * What been changed:
 * chat behaves exactly as before. Only the HTTP plumbing moved into post.
 * embed(List<String>) is the batch call. One HTTP request embeds many chunks, which matters on a CPU where per-request overhead adds up. The ingestion service will send batches of around 16 chunks, so one slow request stays well inside the 90s read timeout.
 * embed(String) is a convenience for one text. Stage 3 will use it for the question.
 * Every vector is checked against embeddingDimensions before anything reaches the database.
 * */

@Component
public class OllamaClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    private final RestClient restClient;
    private final OllamaProperties props;

    public OllamaClient(RestClient ollamaRestClient, OllamaProperties props) {
        this.restClient = ollamaRestClient;
        this.props = props;
    }

    /**
     * One non-streaming chat call.
     *
     * @param format JSON schema the reply must follow, or null for free text
     */
    public OllamaChat.Response chat(List<OllamaChat.Message> messages, Map<String, Object> format) {
        var request = new OllamaChat.Request(
                props.chatModel(),
                messages,
                false,                          // one complete JSON reply, no token stream
                format,
                Map.of("temperature", 0)        // deterministic-ish: same question, same answer
        );

        OllamaChat.Response response = post("/api/chat", request, OllamaChat.Response.class, props.chatModel());

        if (response == null || response.message() == null || response.message().content() == null) {
            throw new OllamaException(OllamaException.Kind.BAD_RESPONSE, "Ollama returned an empty response");
        }
        if ("length".equals(response.doneReason())) {
            throw new OllamaException(OllamaException.Kind.BAD_RESPONSE,
                    "Ollama output was cut off before it finished");
        }
        return response;
    }

    /** Embeds a batch of texts; the returned list has one vector per input, in order. */
    private List<float[]> embedRaw(List<String> inputs) {
        if (inputs.isEmpty()) {
            return List.of();
        }
        var request = new OllamaEmbed.Request(props.embeddingModel(), inputs, false);

        OllamaEmbed.Response response =
                post("/api/embed", request, OllamaEmbed.Response.class, props.embeddingModel());

        if (response == null || response.embeddings() == null
                || response.embeddings().size() != inputs.size()) {
            throw new OllamaException(OllamaException.Kind.BAD_RESPONSE,
                    "Ollama returned a wrong number of embeddings");
        }
        for (float[] vector : response.embeddings()) {
            if (vector == null || vector.length != props.embeddingDimensions()) {
                throw new OllamaException(OllamaException.Kind.BAD_RESPONSE,
                        "Embedding has " + (vector == null ? 0 : vector.length) + " dimensions, expected "
                                + props.embeddingDimensions() + " (model '" + props.embeddingModel() + "')");
            }
        }
        return response.embeddings();
    }

    /** Embeds text that will be stored and searched later (chunks). Adds the document prefix. */
    public List<float[]> embedDocuments(List<String> texts) {
        return embedRaw(texts.stream().map(t -> props.documentPrefix() + t).toList());
    }

    /** Embeds a user question for searching. Adds the query prefix. */
    public float[] embedQuery(String question) {
        return embedRaw(List.of(props.queryPrefix() + question)).get(0);
    }

    /*public float[] embed(String input) {
        return embed(List.of(input)).get(0);
    }*/

    private <T> T post(String uri, Object body, Class<T> type, String model) {
        try {
            return restClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(type);
        } catch (ResourceAccessException e) {
            if (hasCause(e, HttpTimeoutException.class)) {
                log.warn("Ollama call {} timed out after {}", uri, props.readTimeout());
                throw new OllamaException(OllamaException.Kind.TIMEOUT,
                        "Ollama did not answer within " + props.readTimeout(), e);
            }
            log.warn("Ollama not reachable at {}: {}", props.baseUrl(), e.getMessage());
            throw new OllamaException(OllamaException.Kind.UNAVAILABLE,
                    "Ollama is not reachable at " + props.baseUrl(), e);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String hint = status == 404 ? " (is the model '" + model + "' pulled?)" : "";
            log.warn("Ollama {} returned HTTP {}: {}", uri, status, e.getResponseBodyAsString());
            throw new OllamaException(OllamaException.Kind.BAD_RESPONSE,
                    "Ollama returned HTTP " + status + hint, e);
        }
    }

    private static boolean hasCause(Throwable t, Class<? extends Throwable> type) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (type.isInstance(c)) {
                return true;
            }
        }
        return false;
    }

}













