package com.example.hellospring;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class UserService {
    private final UserRepository repository;

    public UserService(UserRepository repository) { this.repository = repository; }

    public List<User> findAll() { return repository.findAll(); }

    public User getById(long id) {
        return repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь с ID " + id + " не найден"));
    }

    public User create(CreateUserRequest request) { return repository.save(request.name().trim()); }
}
