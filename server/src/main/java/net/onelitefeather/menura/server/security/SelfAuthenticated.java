package net.onelitefeather.menura.server.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a route that authenticates its own credential, such as the calendar feed token in the path. The access
 * filter does not ask {@link CurrentPerson} for such a route; the handler verifies the credential and answers 401
 * itself. Use it only where the credential is not a person's session.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SelfAuthenticated {
}
