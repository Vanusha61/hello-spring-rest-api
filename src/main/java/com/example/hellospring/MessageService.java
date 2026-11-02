package com.example.hellospring;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
public class MessageService {
    private final JdbcMessageRepository messages;
    private final UserRepository users;

    public MessageService(JdbcMessageRepository messages, UserRepository users) {
        this.messages = messages;
        this.users = users;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MessagePage getMessages(int page, int size, String author) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page должен быть >= 0, size — от 1 до 100");
        }
        String filter = author == null ? null : author.trim();
        long total = messages.count(filter);
        return new MessagePage(messages.findPage(page, size, filter), page, size, total, (total + size - 1) / size);
    }

    public Message getById(long id) {
        return messages.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Сообщение с ID " + id + " не найдено"));
    }

    @Transactional
    public Message saveMessage(CreateMessageRequest request) {
        User user = resolveUser(request);
        return messages.save(request.content(), user.id());
    }

    @Transactional
    public int saveBatch(List<CreateMessageRequest> requests) {
        // Проверяем всех пользователей до batchUpdate. Ошибка откатывает и новые записи пользователей.
        List<JdbcMessageRepository.NewMessage> batch = new ArrayList<>(requests.size());
        for (CreateMessageRequest request : requests) {
            batch.add(new JdbcMessageRepository.NewMessage(request.content(), resolveUser(request).id()));
        }
        messages.saveBatch(batch);
        return batch.size();
    }

    @Transactional
    public void delete(long id) {
        if (messages.deleteById(id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Сообщение с ID " + id + " не найдено");
        }
    }

    private User resolveUser(CreateMessageRequest request) {
        String author = request.author() == null ? "" : request.author().trim();
        if (request.userId() != null) {
            User user = users.findById(request.userId()).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь с ID " + request.userId() + " не найден"));
            if (!author.isEmpty() && !author.equals(user.name())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "author не соответствует пользователю userId");
            }
            return user;
        }
        return users.findOrCreate(author.isEmpty() ? "Anonymous" : author);
    }
}
