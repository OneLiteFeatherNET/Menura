package net.onelitefeather.menura.server.web;

/**
 * What one caller may do with one object. Built per request from the caller's roles and identity.
 *
 * <p>{@link #isVisible} decides whether the caller may know the object exists at all; {@link #mayRead} decides
 * whether a caller who may see it holds the right to read it.
 */
public interface ObjectAccess<T> {

    boolean isVisible(T object);

    boolean mayRead(T object);
}
