package org.leap.executor.exec;

@FunctionalInterface
public interface BackoffSleeper {
    void sleep(long millis) throws InterruptedException;
}