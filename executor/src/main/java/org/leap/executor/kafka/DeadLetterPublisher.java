package org.leap.executor.kafka;

public interface DeadLetterPublisher {
    void sendToDlt(String originalTopic, String key, byte[] originalMessageValue, String failureReason);
}
