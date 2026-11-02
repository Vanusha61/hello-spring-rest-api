package com.example.hellospring;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class JdbcMessageRepository {
    private final JdbcTemplate jdbc;
    private static final String SELECT = "SELECT m.id, m.content, u.name AS author, m.user_id, m.created_at "
            + "FROM messages m JOIN app_users u ON u.id = m.user_id";
    private static final RowMapper<Message> MAPPER = (rs, row) -> new Message(
            rs.getLong("id"), rs.getString("content"), rs.getString("author"),
            rs.getLong("user_id"), rs.getTimestamp("created_at").toLocalDateTime());

    public JdbcMessageRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<Message> findById(long id) {
        return jdbc.query(SELECT + " WHERE m.id = ?", MAPPER, id).stream().findFirst();
    }

    public Message save(String content, long userId) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO messages(content, user_id) VALUES (?, ?)", new String[]{"id"});
            statement.setString(1, content);
            statement.setLong(2, userId);
            return statement;
        }, keys);
        return findById(Objects.requireNonNull(keys.getKey()).longValue()).orElseThrow();
    }

    public List<Message> findPage(int page, int size, String author) {
        long offset = (long) page * size;
        String order = " ORDER BY m.created_at DESC, m.id DESC LIMIT ? OFFSET ?";
        return author == null
                ? jdbc.query(SELECT + order, MAPPER, size, offset)
                : jdbc.query(SELECT + " WHERE u.name = ?" + order, MAPPER, author, size, offset);
    }

    public long count(String author) {
        Long result = author == null ? jdbc.queryForObject("SELECT COUNT(*) FROM messages", Long.class)
                : jdbc.queryForObject("SELECT COUNT(*) FROM messages m JOIN app_users u ON u.id = m.user_id WHERE u.name = ?", Long.class, author);
        return Objects.requireNonNull(result);
    }

    public int deleteById(long id) {
        return jdbc.update("DELETE FROM messages WHERE id = ?", id);
    }

    public void saveBatch(List<NewMessage> messages) {
        jdbc.batchUpdate("INSERT INTO messages(content, user_id) VALUES (?, ?)", new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement statement, int index) throws SQLException {
                statement.setString(1, messages.get(index).content());
                statement.setLong(2, messages.get(index).userId());
            }

            @Override
            public int getBatchSize() { return messages.size(); }
        });
    }

    public record NewMessage(String content, long userId) { }
}
