package net.onelitefeather.menura.server.health;

import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Singleton
public class DataSourceProbe implements DatabaseProbe {

    private static final int VALIDATION_TIMEOUT_SECONDS = 2;

    private final DataSource dataSource;

    public DataSourceProbe(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean isReachable() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(VALIDATION_TIMEOUT_SECONDS);
        } catch (SQLException e) {
            return false;
        }
    }
}
