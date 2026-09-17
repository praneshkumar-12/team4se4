package org.leap.executor.db;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Supplies a JDBC connection. Repositories depend on this instead of a
 * concrete driver so tests can point them at an in-memory database — the
 * production implementation is {@link PostgresConnectionFactory}.
 */
@FunctionalInterface
public interface ConnectionFactory {
    Connection getConnection() throws SQLException;
}
