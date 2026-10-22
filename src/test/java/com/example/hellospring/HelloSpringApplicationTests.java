package com.example.hellospring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HelloSpringApplicationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MessageRepository repository;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${server.servlet.context-path}")
    private String contextPath;

    @BeforeEach
    void clearMessages() {
        repository.clear();
    }

    @Test
    void rootEndpointUsesApiContextPath() throws Exception {
        assertThat(contextPath).isEqualTo("/api");
        mockMvc.perform(get("/api/").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello, World!"));
    }

    @Test
    void returnsStoredMessagesInOrder() throws Exception {
        repository.save(new Message("Hello, Spring!"));
        repository.save(new Message("In-memory storage works"));
        mockMvc.perform(get("/api/messages").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].content").value("Hello, Spring!"))
                .andExpect(jsonPath("$[1].content").value("In-memory storage works"))
                .andExpect(jsonPath("$[0].author").value("System"));
    }

    @Test
    void createsMessageWithAuthorAndGeneratedId() throws Exception {
        mockMvc.perform(post("/api/messages").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("  Hello from a player  ", "  Alice  ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", greaterThan(0)))
                .andExpect(jsonPath("$.content").value("Hello from a player"))
                .andExpect(jsonPath("$.author").value("Alice"));

        mockMvc.perform(get("/api/messages").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].author").value("Alice"));
    }

    @Test
    void rejectsBlankAndMissingFieldsWithoutSaving() throws Exception {
        for (String body : new String[]{json(" ", "Alice"), json("Hello", "\t"), "{}",
                "{\"content\":\"Hello\"}", "{\"author\":\"Alice\"}"}) {
            mockMvc.perform(post("/api/messages").contextPath("/api")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").exists());
        }
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void rejectsOversizedFields() throws Exception {
        for (String body : new String[]{json("a".repeat(2001), "Alice"), json("Hello", "a".repeat(101))}) {
            mockMvc.perform(post("/api/messages").contextPath("/api")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/messages").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void returnsOnlyTheLastThirtyMessagesInAscendingIdOrder() throws Exception {
        for (int index = 1; index <= 35; index++) {
            mockMvc.perform(post("/api/messages").contextPath("/api")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("Message " + index, "Alice")))
                    .andExpect(status().isCreated());
        }

        String response = mockMvc.perform(get("/api/messages").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(30))
                .andExpect(jsonPath("$[0].content").value("Message 6"))
                .andExpect(jsonPath("$[29].content").value("Message 35"))
                .andReturn().getResponse().getContentAsString();
        var messages = objectMapper.readTree(response);
        long previousId = -1;
        for (var message : messages) {
            long id = message.get("id").asLong();
            assertThat(id).isGreaterThan(previousId);
            previousId = id;
        }
        assertThat(repository.findAll()).hasSize(35);
    }

    private String json(String content, String author) throws Exception {
        return objectMapper.writeValueAsString(Map.of("content", content, "author", author));
    }
}
