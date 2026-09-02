package org.leap.port;

import org.leap.domain.Instrument;

import java.util.Optional;

public interface InstrumentProvider {

    Optional<Instrument> findById(String instrumentId);
}
