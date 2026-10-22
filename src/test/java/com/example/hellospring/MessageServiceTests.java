package com.example.hellospring;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageServiceTests {
    @Test
    void initializesSampleMessages() {
        MessageRepository repository = new MessageRepository();
        MessageService service = new MessageService(repository);

        assertThat(service.getAllMessages()).extracting(Message::getContent)
                .containsExactly("Hello, Spring!", "In-memory storage works");
        assertThat(service.getAllMessages()).extracting(Message::getAuthor)
                .containsOnly("System");
    }
}
