package net.onelitefeather.menura.server.auth;

import net.onelitefeather.menura.core.auth.Role;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares who may call an endpoint. Put it on a controller method; the role check runs before the handler, so a
 * refused request performs no action.
 *
 * <ul>
 *   <li>{@code @RequiresRole(Role.EDITOR)}: a person holding the editor role.</li>
 *   <li>{@code @RequiresRole({Role.EDITOR, Role.ADMIN})}: a person holding any one of the listed roles.</li>
 *   <li>{@code @RequiresRole} with no value: any signed-in person, whatever roles they hold.</li>
 * </ul>
 *
 * <p>Refusals: 401 when no verified person is present, 403 when the person holds none of the listed roles. Both are
 * Problem Details. An endpoint without this annotation is not checked here; the anonymous allow-list of the audit
 * and secret component decides which of those are reachable without a person.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequiresRole {

    Role[] value() default {};
}
