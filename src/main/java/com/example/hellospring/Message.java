package com.example.hellospring;

import java.util.Objects;

/** A plain Java object representing one message. */
public class Message {
    private Long id;
    private String content;
    private String author;

    public Message() {
    }

    public Message(String content) {
        this(content, "System");
    }

    public Message(String content, String author) {
        this.content = content;
        this.author = author;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Message message)) {
            return false;
        }
        return Objects.equals(id, message.id)
                && Objects.equals(content, message.content)
                && Objects.equals(author, message.author);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, content, author);
    }
}
