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
                false,
                format,
                Map.of("temperature", 0)
        );

        OllamaChat.Response response;

        try {
            response = restClient.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(OllamaChat.Response.class);

        } catch (ResourceAccessException e) {
            if(hasCause(e, HttpTimeoutException.class)) {
                log.warn("Ollama call timed out after {}", props.readTimeout());
                throw new OllamaException(OllamaException.Kind.TIMEOUT,
                        "Ollama did not answer within " + props.readTimeout(), e);
            }
            log.warn("Ollama not reachable at {}: {}", props.baseUrl(), e.getMessage());
            throw new OllamaException(OllamaException.Kind.UNAVAILABLE,
                    "Ollama is not reachable at " + props.baseUrl(), e);

        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String hintAdvice = status == 404 ? " (is the model '" + props.chatModel() + "' pulled?)" : "";
            log.warn("Ollama returned HTTP status {}: {}", status, e.getResponseBodyAsString());
            throw new OllamaException(OllamaException.Kind.BAD_RESPONSE,
                    "Ollama returned HTTP " + status + hintAdvice, e);

        }

        if(response == null || response.message() == null || response.message().content() == null){
            throw new OllamaException(OllamaException.Kind.BAD_RESPONSE, "Ollama returned an empty response");
        }
        if("length".equals(response.doneReason())){
            throw new OllamaException(OllamaException.Kind.BAD_RESPONSE,
                    "Ollama output was cut off before it finished");
        }

        return response;


    }


    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable c = throwable; c != null; c = c.getCause()) {
            if (type.isInstance(c)) {
                return true;
            }
        }
        return false;
    }

}













