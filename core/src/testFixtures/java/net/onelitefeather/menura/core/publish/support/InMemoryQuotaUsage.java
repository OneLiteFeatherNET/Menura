package net.onelitefeather.menura.core.publish.support;

import net.onelitefeather.menura.core.publish.QuotaLedger;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Quota usage per channel and day, held in memory. */
public final class InMemoryQuotaUsage implements QuotaLedger.Store {

    private final Map<String, QuotaLedger.Usage> usage = new HashMap<>();

    @Override
    public QuotaLedger.Usage usage(Connection connection, UUID channelId, LocalDate day) {
        return usage.getOrDefault(channelId + "/" + day, new QuotaLedger.Usage(0, 0));
    }

    @Override
    public void save(Connection connection, UUID channelId, LocalDate day, QuotaLedger.Usage value) {
        usage.put(channelId + "/" + day, value);
    }
}
