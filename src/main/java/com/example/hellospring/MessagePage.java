package com.example.hellospring;

import java.util.List;

public record MessagePage(List<Message> content, int page, int size, long totalElements, long totalPages) {
}
