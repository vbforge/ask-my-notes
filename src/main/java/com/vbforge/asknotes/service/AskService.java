package com.vbforge.asknotes.service;

import com.vbforge.asknotes.api.AskResponse;
import com.vbforge.asknotes.ollama.OllamaChat;
import com.vbforge.asknotes.ollama.OllamaClient;
import com.vbforge.asknotes.ollama.OllamaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/**
 * Why it looks like this:
 *
 * The prompt and the schema both ask for the same shape. The format schema makes Ollama constrain
 * generation so the output is syntactically valid JSON with those fields.
 * The prompt also tells the model what the fields mean, which helps it fill them sensibly, and Ollama's own docs recommend doing both.
 * We still validate after parsing. The schema guarantees structure, not meaning.
 * The model can legally return {"answer": "", "confidence": "high"}.
 * Two layers catch problems: parse failure (including a bad confidence value, which our enum rejects) and the blank or incomplete check.
 * Both failures become BAD_RESPONSE, which will map to a clean 502 instead of a stack trace.
 * Truncated logging. When the model misbehaves we log the first 200 characters, enough to debug without flooding the log.
 * JsonMapper is injected. It's the Boot-configured Jackson 3 mapper, and JacksonException is unchecked, so there's no throws clutter.
 * */

@Service
public class AskService {

    private final Logger log = LoggerFactory.getLogger(AskService.class);

//    private final String SYSTEM_PROMPT = """
//            You are a helpful assistant. Answer the user's question accurately and concisely.
//            Reply ONLY with a JSON object with exactly two fields:
//            - "answer": your answer as plain text
//            - "confidence": one of "low", "medium", "high", reflecting how sure you are
//            If you are not sure or do not know, say so in the answer and use "low" confidence.
//            """;

    /**
     * JSON schema Ollama enforces while generating.
     */
    private static final Map<String, Object> ANSWER_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "answer", Map.of("type", "string"),
                    "confidence", Map.of("type", "string", "enum", List.of("low", "medium", "high"))
            ),
            "required", List.of("answer", "confidence")
    );

    private final OllamaClient ollama;
    private final JsonMapper jsonMapper;

    public AskService(OllamaClient ollama, JsonMapper jsonMapper) {
        this.ollama = ollama;
        this.jsonMapper = jsonMapper;
    }

    public AskResponse ask(String question){

        String SYSTEM_PROMPT = """
            You are a helpful assistant. Answer the user's question accurately and concisely.
            Reply ONLY with a JSON object with exactly two fields:
            - "answer": your answer as plain text
            - "confidence": one of "low", "medium", "high", reflecting how sure you are
            If you are not sure or do not know, say so in the answer and use "low" confidence.
            """;

        OllamaChat.Response reply = ollama.chat(
                List.of(OllamaChat.Message.system(SYSTEM_PROMPT), OllamaChat.Message.user(question)),
                ANSWER_SCHEMA
        );

        return parse(reply.message().content());
    }

    private AskResponse parse(String content){
        AskResponse parsed;

        try{
            parsed = jsonMapper.readValue(content, AskResponse.class);
        }catch (JacksonException e){
            log.warn("Model returned unparseable output: {}", abbreviate(content));
            throw new OllamaException(OllamaException.Kind.BAD_RESPONSE,
                    "Model returned invalid structured output", e);
        }

        if(parsed == null || parsed.answer() == null || parsed.answer().isBlank() || parsed.confidence() == null){
            log.warn("Model returned incomplete output: {}", abbreviate(content));
            throw new OllamaException(OllamaException.Kind.BAD_RESPONSE,
                    "Model returned an empty or incomplete answer");
        }

        return new AskResponse(parsed.answer().trim(), parsed.confidence());

    }

    private static String abbreviate(String s) {
        return s.length() <= 200 ? s : s.substring(0, 200) + "...";
    }


}




















