package net.casual.arcade.resources

import java.io.InputStream

/**
 * An interface for providing a resource pack to
 * a [PackHost].
 *
 * @see PathPack
 * @see PackHost
 */
public interface ReadablePack {
    /**
     * The name of the pack.
     * This may or may not end in `.zip`.
     */
    public val name: String

    /**
     * This streams the contents of the [ReadablePack].
     *
     * @return The [InputStream] for the pack.
     */
    public fun stream(): InputStream

    /**
     * Checks whether the pack is currently readable.
     *
     * @return Whether the pack is readable.
     */
    public fun readable(): Boolean {
        return true
    }

    /**
     * This gets the number of bytes of the [ReadablePack].
     * This may return 0 if the exact number is not known.
     *
     * @return The size of the pack in bytes.
     */
    public fun length(): Long {
        return 0
    }
}