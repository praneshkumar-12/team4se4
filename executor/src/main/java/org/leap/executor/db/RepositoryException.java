package org.leap.executor.db;

/** Wraps a {@link java.sql.SQLException} as unchecked, at the repository boundary. */
public class RepositoryException extends RuntimeException {
    public RepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
