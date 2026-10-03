package com.vbforge.asknotes.api;

import com.vbforge.asknotes.service.AskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * **Why so thin:** the controller only handles HTTP: it takes the body, triggers validation through `@Valid`,
 * calls the service and returns the result. Prompting and parsing stay in `AskService`, and error mapping lives in the handler below.
 * */

@RestController
public class AskController {

    private final AskService askService;

    public AskController(AskService askService) {
        this.askService = askService;
    }

    @PostMapping("/ask")
    public AskResponse ask(@Valid @RequestBody AskRequest askRequest) {
        return askService.ask(askRequest.question());
    }

}
