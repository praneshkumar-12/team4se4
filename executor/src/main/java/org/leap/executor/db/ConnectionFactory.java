public package org.leap.executor.db;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface ConnectionFactory {
    Connection getConnection() throws SQLException;
} 