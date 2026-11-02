package net.onelitefeather.menura.server.web;

import java.util.Optional;

/** Reads one object by its identifier. Implemented by each repository; the visibility rule stays in the guard. */
public interface ObjectRepository<I, T> {

    Optional<T> findById(I id);
}
