package com.vbforge.asknotes.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/** Wire format of Ollama's POST /api/chat.
 *
 * **Why:** these records mirror the JSON you saw in your curl output. `ignoreUnknown` means new fields Ollama adds later won't break parsing.
 * We keep only the fields we use. `done_reason` tells us whether generation was cut off, and `total_duration` (nanoseconds)
 * will feed latency logging in Stage 4. Nesting the three records in one holder class keeps the wire format in one file.
 * */
public final class OllamaChat {

    private OllamaChat() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(String role, String content){

        public static Message system(String content){
            return new Message("system", content);
        }

        public static Message user(String content){
            return new Message("user", content);
        }

    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Request(
            String model,
            List<Message> messages,
            boolean stream,
            Map<String, Object> format,
            Map<String, Object> options
    ){
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(
            Message message,
            boolean done,
            @JsonProperty("done_reason") String doneReason,
            @JsonProperty("total_duration") Long totalDuration
    ){
    }




}
