package net.onelitefeather.menura.server.library;

import java.sql.SQLException;
import java.util.Set;

/** The layout template formats of the brand kit. A timeline may only refer to one of them. */
@FunctionalInterface
public interface LayoutTemplates {

    /** The format of every layout template of the newest brand kit version, such as {@code short-9x16}. */
    Set<String> formats() throws SQLException;
}
