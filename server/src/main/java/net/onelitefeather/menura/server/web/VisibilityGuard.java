package net.onelitefeather.menura.server.web;

import net.onelitefeather.menura.core.problem.ProblemException;

import java.util.Objects;

/**
 * Reusable visibility rule for object reads (principle 11: objects a caller may not see answer 404).
 *
 * <ul>
 *   <li>Missing or hidden object: 404. Both carry the same Problem Details, so the answer does not reveal
 *       whether a hidden object exists.</li>
 *   <li>Visible object without the right to read it: 403.</li>
 * </ul>
 *
 * <p>Visibility is checked before the right, so a caller who may not see an object never learns anything from
 * the right check. The errors are {@link ProblemException}s, which the server sends as RFC 9457 Problem Details.
 */
public final class VisibilityGuard<I, T> {

    private final ObjectRepository<I, T> repository;
    private final String objectName;

    public VisibilityGuard(ObjectRepository<I, T> repository, String objectName) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.objectName = Objects.requireNonNull(objectName, "objectName");
    }

    public T read(I id, ObjectAccess<T> access) {
        T object = repository.findById(id).orElseThrow(this::notFound);
        if (!access.isVisible(object)) {
            throw notFound();
        }
        if (!access.mayRead(object)) {
            throw new ProblemException(403, "Forbidden", "The caller may not read this " + objectName + ".");
        }
        return object;
    }

    private ProblemException notFound() {
        return new ProblemException(404, "Not Found", "No " + objectName + " with this id.");
    }
}
