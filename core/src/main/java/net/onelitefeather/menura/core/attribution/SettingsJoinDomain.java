package net.onelitefeather.menura.core.attribution;

import net.onelitefeather.menura.core.series.JoinDomain;

import java.sql.SQLException;
import java.util.Optional;
import java.util.Objects;

/** The join domain of the installation's settings, as the render bundle of the content series reads it. */
public final class SettingsJoinDomain implements JoinDomain {

    private final JoinService joins;

    public SettingsJoinDomain(JoinService joins) {
        this.joins = Objects.requireNonNull(joins, "joins");
    }

    @Override
    public Optional<String> joinDomain() {
        try {
            return Optional.ofNullable(joins.settings().joinDomain());
        } catch (SQLException unreadable) {
            throw new IllegalStateException("the join domain could not be read", unreadable);
        }
    }
}
