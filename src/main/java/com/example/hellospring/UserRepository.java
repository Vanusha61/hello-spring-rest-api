package com.example.hellospring;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbc;
    private static final RowMapper<User> MAPPER = (rs, row) -> new User(rs.getLong("id"), rs.getString("name"));

    public UserRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<User> findAll() {
        return jdbc.query("SELECT id, name FROM app_users ORDER BY id", MAPPER);
    }

    public Optional<User> findById(long id) {
        return jdbc.query("SELECT id, name FROM app_users WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public Optional<User> findByName(String name) {
        return jdbc.query("SELECT id, name FROM app_users WHERE name = ?", MAPPER, name).stream().findFirst();
    }

    public User save(String name) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO app_users(name) VALUES (?)", new String[]{"id"});
            statement.setString(1, name);
            return statement;
        }, keys);
        return new User(Objects.requireNonNull(keys.getKey()).longValue(), name);
    }

    public User findOrCreate(String name) {
        return findByName(name).orElseGet(() -> save(name));
    }
}
