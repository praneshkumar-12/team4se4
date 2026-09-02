package org.leap.port;

import org.leap.domain.Position;

import java.util.Optional;

public interface PositionProvider {

    Optional<Position> findByAccountIdAndInstrumentId(
            String accountId,
            String instrumentId
    );

    void save(Position position);
}
