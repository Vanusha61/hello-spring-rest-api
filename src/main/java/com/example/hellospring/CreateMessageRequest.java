package com.example.hellospring;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateMessageRequest(
        @NotBlank @Size(max = 255) String content,
        @Size(max = 100) String author,
        @Positive Long userId) {
}
