package com.example.hellospring;

import jakarta.validation.Valid;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/messages")
public class MessageController {
    private final MessageService service;
    private final Validator validator;

    public MessageController(MessageService service, Validator validator) {
        this.service = service;
        this.validator = validator;
    }

    @GetMapping
    public MessagePage getMessages(@RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "30") int size,
                                   @RequestParam(required = false) String author) {
        return service.getMessages(page, size, author);
    }

    @GetMapping("/{id}")
    public Message getMessage(@PathVariable long id) { return service.getById(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Message createMessage(@Valid @RequestBody CreateMessageRequest request) {
        return service.saveMessage(request);
    }

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Integer> createBatch(@RequestBody List<CreateMessageRequest> requests) {
        if (requests.isEmpty() || requests.size() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Пакет должен содержать от 1 до 1000 сообщений");
        }
        // Валидация каждого элемента массива, включая null, до начала транзакции.
        for (int i = 0; i < requests.size(); i++) {
            CreateMessageRequest request = requests.get(i);
            if (request == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Сообщение " + i + " не может быть null");
            }
            var violations = validator.validate(request);
            if (!violations.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Сообщение " + i + ": " + violations.iterator().next().getPropertyPath() + " — "
                                + violations.iterator().next().getMessage());
            }
        }
        return Map.of("createdCount", service.saveBatch(requests));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMessage(@PathVariable long id) { service.delete(id); }
}
