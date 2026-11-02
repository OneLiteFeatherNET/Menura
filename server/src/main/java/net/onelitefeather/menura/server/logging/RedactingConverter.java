package net.onelitefeather.menura.server.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.CompositeConverter;

/**
 * Logback conversion word {@code %redact(...)}: redacts secrets from the text its sub-pattern produces. Used in
 * logback.xml around the message and the stack trace, so both pass through {@link LogRedaction}.
 */
public class RedactingConverter extends CompositeConverter<ILoggingEvent> {

    @Override
    protected String transform(ILoggingEvent event, String in) {
        return LogRedaction.redact(in);
    }
}
