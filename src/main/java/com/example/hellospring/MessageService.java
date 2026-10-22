package com.example.hellospring;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MessageService {
    private static final int RECENT_MESSAGES_LIMIT = 30;
    private final MessageRepository repository;

    public MessageService(MessageRepository repository) {
        this.repository = repository;
        initializeSampleData();
    }

    private void initializeSampleData() {
        repository.save(new Message("Hello, Spring!"));
        repository.save(new Message("In-memory storage works"));
    }

    /** Last 30 messages, in ascending ID order (oldest first within the result). */
    public List<Message> getAllMessages() {
        List<Message> messages = repository.findAll();
        int start = Math.max(0, messages.size() - RECENT_MESSAGES_LIMIT);
        return List.copyOf(messages.subList(start, messages.size()));
    }

    public Message createMessage(String content, String author) {
        return repository.save(new Message(content.strip(), author.strip()));
    }
}
