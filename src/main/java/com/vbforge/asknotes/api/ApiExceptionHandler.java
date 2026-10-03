package com.vbforge.asknotes.api;

import com.vbforge.asknotes.ollama.OllamaException;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
  **Why:**
     - **Errors become structured JSON with a `detail` field.**
         That's the format the page already reads, so the UI shows readable messages instead of "HTTP 500".
         `ProblemDetail` is Spring's built-in RFC 9457 error type.
     - **Our own validation handler** replaces Spring's generic "Invalid request content" with your annotation's message,
         for example "question must not be blank".
     - **The status codes carry meaning.** 503 means Ollama is down, 504 means it was too slow, and 502 means it answered with garbage.
         None of them is the client's fault, so none is a 4xx.
*/


@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse("invalid request");

        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadable(HttpMessageNotReadableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "request body must be JSON like {\"question\": \"...\"}");
    }

    @ExceptionHandler(OllamaException.class)
    ProblemDetail handleOllama(OllamaException e) {
        HttpStatus status = switch (e.kind()) {
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;   // 503
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;           // 504
            case BAD_RESPONSE -> HttpStatus.BAD_GATEWAY;          // 502
        };
        return ProblemDetail.forStatusAndDetail(status, e.getMessage());
    }


}
