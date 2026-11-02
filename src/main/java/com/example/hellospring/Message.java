package com.example.hellospring;

import java.time.LocalDateTime;

public record Message(Long id, String content, String author, Long userId, LocalDateTime createdAt) {
}
