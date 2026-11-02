package com.example.hellospring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP tests use real JDBC repositories and a real relational database, without mocks. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HelloSpringApplicationTests {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;

    @Test
    void createsMessageWithDatabaseIdTimestampAndUserRelation() throws Exception {
        JsonNode message = createMessage(Map.of("content", "Привет, JDBC!", "author", "Ваня"));
        long id = message.path("id").asLong();
        long userId = message.path("userId").asLong();

        assertThat(id).isPositive();
        assertThat(userId).isPositive();
        assertThat(message.path("createdAt").asText()).isNotBlank();
        assertThat(LocalDateTime.parse(message.path("createdAt").asText())).isNotNull();
        assertThat(jdbc.queryForObject("SELECT content FROM messages WHERE id = ?", String.class, id))
                .isEqualTo("Привет, JDBC!");
        assertThat(jdbc.queryForObject("SELECT user_id FROM messages WHERE id = ?", Long.class, id))
                .isEqualTo(userId);
        assertThat(jdbc.queryForObject("SELECT name FROM app_users WHERE id = ?", String.class, userId))
                .isEqualTo("Ваня");
        assertThat(jdbc.queryForObject("SELECT created_at FROM messages WHERE id = ?", Timestamp.class, id))
                .isNotNull();

        mvc.perform(get("/api/messages/{id}", id).contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Привет, JDBC!"))
                .andExpect(jsonPath("$.author").value("Ваня"))
                .andExpect(jsonPath("$.userId").value(userId));
    }

    @Test
    void missingOrBlankAuthorBecomesAnonymousAndReusesUser() throws Exception {
        JsonNode first = createMessage(Map.of("content", "Без автора"));
        JsonNode second = createMessage(Map.of("content", "Пробелы", "author", "   "));
        assertThat(first.path("author").asText()).isEqualTo("Anonymous");
        assertThat(second.path("author").asText()).isEqualTo("Anonymous");
        assertThat(second.path("userId").asLong()).isEqualTo(first.path("userId").asLong());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_users WHERE name = ?", Long.class, "Anonymous"))
                .isEqualTo(1);
    }

    @Test
    void supportsExistingUserAndRejectsConflictingAuthor() throws Exception {
        JsonNode user = createUser("Tester");
        long userId = user.path("id").asLong();
        JsonNode message = createMessage(Map.of("content", "Existing user", "userId", userId));
        assertThat(message.path("author").asText()).isEqualTo("Tester");
        assertThat(message.path("userId").asLong()).isEqualTo(userId);

        mvc.perform(post("/api/messages").contextPath("/api").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("content", "Conflict", "userId", userId,
                                "author", "Someone else"))))
                .andExpect(status().isBadRequest());
        assertThat(messageCount()).isEqualTo(1);
    }

    @Test
    void createsListsAndFindsUsersAndRejectsDuplicates() throws Exception {
        JsonNode user = createUser("Admin");
        mvc.perform(get("/api/users").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Admin"));
        mvc.perform(get("/api/users/{id}", user.path("id").asLong()).contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Admin"));
        mvc.perform(post("/api/users").contextPath("/api").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Admin\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void ordersByCreationTimeAndIdDescending() throws Exception {
        long firstId = createMessage(Map.of("content", "first", "author", "A")).path("id").asLong();
        long secondId = createMessage(Map.of("content", "second", "author", "A")).path("id").asLong();
        long thirdId = createMessage(Map.of("content", "third", "author", "A")).path("id").asLong();
        // Equal timestamps verify deterministic ordering when inserts happen at the same instant.
        jdbc.update("UPDATE messages SET created_at = ?", Timestamp.valueOf("2026-01-01 10:00:00"));
        jdbc.update("UPDATE messages SET created_at = ? WHERE id = ?",
                Timestamp.valueOf("2026-01-02 10:00:00"), firstId);

        mvc.perform(get("/api/messages").contextPath("/api").param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(firstId))
                .andExpect(jsonPath("$.content[1].id").value(thirdId))
                .andExpect(jsonPath("$.content[2].id").value(secondId));
    }

    @Test
    void paginatesBeyondThirtyAndReturnsEmptyOutOfRangePage() throws Exception {
        List<Map<String, Object>> messages = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            messages.add(Map.of("content", "Message " + i, "author", "Pager"));
        }
        createBatch(messages, 32);
        mvc.perform(get("/api/messages").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(30)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(30))
                .andExpect(jsonPath("$.totalElements").value(32))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/messages").contextPath("/api").param("page", "1").param("size", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.page").value(1));
        mvc.perform(get("/api/messages").contextPath("/api").param("page", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(32));
    }

    @Test
    void filtersByAuthorBeforePagination() throws Exception {
        createBatch(List.of(Map.of("content", "A1", "author", "Alice"),
                Map.of("content", "B1", "author", "Bob"),
                Map.of("content", "A2", "author", "Alice")), 3);
        mvc.perform(get("/api/messages").contextPath("/api")
                        .param("author", "Alice").param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].author").value("Alice"));
        mvc.perform(get("/api/messages").contextPath("/api").param("author", "Nobody"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void batchCreatesMessagesWithExistingAndAnonymousUsers() throws Exception {
        long userId = createUser("Batch user").path("id").asLong();
        createBatch(List.of(Map.of("content", "One", "userId", userId),
                Map.of("content", "Two")), 2);
        assertThat(messageCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM messages WHERE user_id = ?", Long.class, userId))
                .isEqualTo(1);
        mvc.perform(get("/api/messages").contextPath("/api").param("author", "Anonymous"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].createdAt", notNullValue()));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void batchFailureRollsBackMessagesAndNewUsers() throws Exception {
        // The service owns its transaction here, so assertions observe the completed rollback.
        try {
            mvc.perform(post("/api/messages/batch").contextPath("/api").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(List.of(
                                    Map.of("content", "Should roll back", "author", "Temporary"),
                                    Map.of("content", "Missing user", "userId", Long.MAX_VALUE)))))
                    .andExpect(status().isNotFound());
            assertThat(messageCount()).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_users WHERE name = ?", Long.class, "Temporary"))
                    .isZero();
        } finally {
            // Also isolate later tests if a regression accidentally commits part of the batch.
            jdbc.update("DELETE FROM messages WHERE user_id IN (SELECT id FROM app_users WHERE name = ?)",
                    "Temporary");
            jdbc.update("DELETE FROM app_users WHERE name = ?", "Temporary");
        }
    }

    @Test
    void deletesMessageAndReturns404ForMissingResources() throws Exception {
        long id = createMessage(Map.of("content", "Delete me", "author", "Tester")).path("id").asLong();
        mvc.perform(delete("/api/messages/{id}", id).contextPath("/api"))
                .andExpect(status().isNoContent());
        assertThat(messageCount()).isZero();
        mvc.perform(get("/api/messages/{id}", id).contextPath("/api"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail", notNullValue()));
        mvc.perform(delete("/api/messages/{id}", id).contextPath("/api"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/users/{id}", Long.MAX_VALUE).contextPath("/api"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/messages").contextPath("/api").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Unknown user\",\"userId\":" + Long.MAX_VALUE + "}"))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @CsvSource({"-1,30", "0,0", "0,-1", "0,101", "abc,10", "0,abc"})
    void rejectsInvalidPagination(String page, String size) throws Exception {
        mvc.perform(get("/api/messages").contextPath("/api").param("page", page).param("size", size))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidMessageContentAndAuthor() throws Exception {
        for (Map<String, Object> body : List.<Map<String, Object>>of(
                Map.of("author", "A"),
                Map.of("content", "   "),
                Map.of("content", "x".repeat(256)),
                Map.of("content", "valid", "author", "x".repeat(101)))) {
            mvc.perform(post("/api/messages").contextPath("/api").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());
        }
        assertThat(messageCount()).isZero();
    }

    @Test
    void rejectsInvalidUserNames() throws Exception {
        for (String body : List.of("{}", "{\"name\":null}", "{\"name\":\"   \"}",
                json.writeValueAsString(Map.of("name", "x".repeat(101))))) {
            mvc.perform(post("/api/users").contextPath("/api").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void rejectsMalformedJsonAndInvalidBatchSizesOrElements() throws Exception {
        mvc.perform(post("/api/messages").contextPath("/api").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":"))
                .andExpect(status().isBadRequest());
        for (String body : List.of("[]", "null", "[null]", "[{\"content\":\"\"}]")) {
            mvc.perform(post("/api/messages/batch").contextPath("/api").contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
        List<Map<String, String>> tooMany = new ArrayList<>();
        for (int i = 0; i < 1001; i++) {
            tooMany.add(Map.of("content", "Message " + i));
        }
        mvc.perform(post("/api/messages/batch").contextPath("/api").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(tooMany)))
                .andExpect(status().isBadRequest());
        assertThat(messageCount()).isZero();
    }

    private JsonNode createMessage(Map<String, ?> body) throws Exception {
        return json.readTree(mvc.perform(post("/api/messages").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode createUser(String name) throws Exception {
        return json.readTree(mvc.perform(post("/api/users").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("name", name))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private void createBatch(List<? extends Map<String, ?>> bodies, int expectedCount) throws Exception {
        mvc.perform(post("/api/messages/batch").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(bodies)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdCount").value(expectedCount));
    }

    private long messageCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM messages", Long.class);
    }
}
