package net.onelitefeather.menura.server.web;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** In-memory repository for the visibility tests; counts lookups so tests can assert what was read. */
final class FakeObjectRepository<I, T> implements ObjectRepository<I, T> {

    private final Map<I, T> objects = new HashMap<>();
    private int lookups;

    FakeObjectRepository<I, T> with(I id, T object) {
        objects.put(id, object);
        return this;
    }

    @Override
    public Optional<T> findById(I id) {
        lookups++;
        return Optional.ofNullable(objects.get(id));
    }

    int lookups() {
        return lookups;
    }
}
