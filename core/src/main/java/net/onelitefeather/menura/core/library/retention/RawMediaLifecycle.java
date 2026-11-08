package net.onelitefeather.menura.core.library.retention;

/**
 * The object store side of retention: moves one recording to the cold storage class. Production implements it with the
 * S3 API (a copy of the object onto itself in the cold class); unit tests use a recording fake.
 */
public interface RawMediaLifecycle {

    void moveToCold(String storageKey) throws Exception;
}
