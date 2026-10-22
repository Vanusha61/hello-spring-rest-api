package com.example.hellospring;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class MessageRepositoryTests {
    @Test
    void supportsCreateReadUpdateDeleteAndClear() {
        MessageRepository repository = new MessageRepository();
        Message first = repository.save(new Message("First", "Alice"));
        Message second = repository.save(new Message("Second", "Bob"));

        assertThat(first.getId()).isEqualTo(1L);
        assertThat(second.getId()).isEqualTo(2L);
        assertThat(repository.findById(first.getId())).isEqualTo(first);
        assertThat(repository.findAll()).containsExactly(first, second);

        first.setContent("Updated");
        repository.save(first);
        assertThat(repository.findById(first.getId()).getContent()).isEqualTo("Updated");
        assertThat(repository.findAll()).hasSize(2);

        repository.deleteById(first.getId());
        assertThat(repository.findById(first.getId())).isNull();
        assertThat(repository.findAll()).containsExactly(second);
        repository.clear();
        assertThat(repository.findAll()).isEmpty();
        assertThat(repository.save(new Message("After clear")).getId()).isGreaterThan(second.getId());
    }

    @Test
    void preventsChangesOutsideRepositoryFromMutatingStoredMessages() {
        MessageRepository repository = new MessageRepository();
        Message input = new Message("Original", "Alice");
        Message saved = repository.save(input);
        input.setContent("Input changed");
        saved.setContent("Result changed");
        repository.findById(saved.getId()).setContent("Read changed");
        repository.findAll().get(0).setContent("List changed");

        assertThat(repository.findById(saved.getId()).getContent()).isEqualTo("Original");
    }

    @Test
    void concurrentCreatesReceiveUniqueIds() throws Exception {
        MessageRepository repository = new MessageRepository();
        var executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Long>> tasks = IntStream.range(0, 100)
                    .<Callable<Long>>mapToObj(index -> () -> repository.save(new Message("Message " + index)).getId())
                    .toList();
            var results = executor.invokeAll(tasks);
            var ids = new java.util.HashSet<Long>();
            for (var result : results) {
                ids.add(result.get());
            }
            assertThat(ids).hasSize(100);
            assertThat(repository.findAll()).hasSize(100);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void messageEqualityIncludesIdContentAndAuthor() {
        Message first = new Message("Hello", "Alice");
        first.setId(1L);
        Message second = new Message("Hello", "Alice");
        second.setId(1L);
        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
        second.setAuthor("Bob");
        assertThat(first).isNotEqualTo(second);
    }
}
