package org.leap.executor.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;


public final class PostgresConnectionFactory implements ConnectionFactory {

    private final String url;
    private final String user;
    private final String password;

    public PostgresConnectionFactory(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    /** Reads {@code DB_URL}/{@code DB_USER}/{@code DB_PASSWORD} from the environment. */
    public static PostgresConnectionFactory fromEnvironment() {
        return new PostgresConnectionFactory(
                requireEnv("DB_URL"), requireEnv("DB_USER"), requireEnv("DB_PASSWORD"));
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set");
        }
        return value;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }
}
