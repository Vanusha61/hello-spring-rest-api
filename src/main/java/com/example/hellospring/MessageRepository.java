package com.example.hellospring;

import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class MessageRepository {
    private final ConcurrentHashMap<Long, Message> storage = new ConcurrentHashMap<>();
    private final AtomicLong idCounter = new AtomicLong(0);

    /** A new message receives an ID; saving an existing ID replaces its data. */
    public Message save(Message message) {
        Objects.requireNonNull(message, "Message must not be null");
        Long id = message.getId();
        if (id == null) {
            id = idCounter.incrementAndGet();
        } else {
            if (id <= 0) {
                throw new IllegalArgumentException("Message ID must be positive");
            }
            idCounter.accumulateAndGet(id, Math::max);
        }
        Message saved = copy(message);
        saved.setId(id);
        storage.put(id, saved);
        return copy(saved);
    }

    public Message findById(Long id) {
        Message message = storage.get(id);
        return message == null ? null : copy(message);
    }

    /** Return an ordered snapshot. Callers cannot mutate stored instances. */
    public List<Message> findAll() {
        return storage.values().stream()
                .map(MessageRepository::copy)
                .sorted(Comparator.comparing(Message::getId))
                .toList();
    }

    public void deleteById(Long id) {
        storage.remove(id);
    }

    public void clear() {
        storage.clear();
        // Keep IDs monotonic, including when clear is called between requests.
    }

    private static Message copy(Message message) {
        Message result = new Message(message.getContent(), message.getAuthor());
        result.setId(message.getId());
        return result;
    }
}
