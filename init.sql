CREATE TABLE IF NOT EXISTS users (
    login VARCHAR(256) CONSTRAINT login_check CHECK (char_length(login) >= 3),
    passwd VARCHAR(256) CONSTRAINT passwd_check CHECK (char_length(passwd) >= 6),
    username VARCHAR(256) CONSTRAINT username_check CHECK (char_length(username) >= 1),
    role INT,
    is_banned BOOLEAN,
    PRIMARY KEY (login, username)
);

INSERT INTO users (login, passwd, username, role, is_banned)
VALUES ('admin', 'godmode', 'admin', 1, FALSE)
ON CONFLICT DO NOTHING;