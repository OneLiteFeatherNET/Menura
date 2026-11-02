package net.onelitefeather.menura.server.health;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataSourceProbeTest {

    @Test
    void probeReportsUnreachableWhenConnectionIsRefused() {
        DataSource refusing = proxy(DataSource.class, (method, args) -> {
            if (method.getName().equals("getConnection")) {
                throw new SQLException("connection refused");
            }
            throw new UnsupportedOperationException(method.getName());
        });

        assertFalse(new DataSourceProbe(refusing).isReachable(),
                "a refused connection must make the database readiness down");
    }

    @Test
    void probeReportsReachableWhenConnectionIsValid() {
        Connection valid = proxy(Connection.class, (method, args) -> switch (method.getName()) {
            case "isValid" -> true;
            case "close" -> null;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        DataSource source = proxy(DataSource.class, (method, args) -> {
            if (method.getName().equals("getConnection")) {
                return valid;
            }
            throw new UnsupportedOperationException(method.getName());
        });

        assertTrue(new DataSourceProbe(source).isReachable(),
                "a valid connection must make the database readiness up");
    }

    private static <T> T proxy(Class<T> type, Handler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> handler.handle(method, args)));
    }

    @FunctionalInterface
    private interface Handler {
        Object handle(java.lang.reflect.Method method, Object[] args) throws Throwable;
    }
}
