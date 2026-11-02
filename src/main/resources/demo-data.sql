INSERT INTO app_users(name) VALUES ('System'), ('Admin');
INSERT INTO messages(content, user_id) SELECT 'Hello, JDBC Spring!', id FROM app_users WHERE name = 'System';
INSERT INTO messages(content, user_id) SELECT 'Now using H2 database via JdbcTemplate.', id FROM app_users WHERE name = 'Admin';
