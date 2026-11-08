package net.onelitefeather.menura.core.series;

import java.util.Optional;

/**
 * The join domain of this installation, which the render bundle names in its call to action. Provided by the join
 * attribution change where it is available; without it the bundle has no call to action, and no error.
 */
@FunctionalInterface
public interface JoinDomain {

    /** A join domain that is never configured. */
    JoinDomain NONE = Optional::empty;

    Optional<String> joinDomain();
}
