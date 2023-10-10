package net.casual.arcade.utils.impl

import org.jetbrains.annotations.ApiStatus.Internal

@Internal
public class ConcatenatedList<E> private constructor(
    internal val lists: MutableList<List<E>>
): AbstractList<E>() {
    override val size: Int
        get() = this.lists.sumOf { it.size }

    override fun get(index: Int): E {
        var passed = 0

        for (list in this.lists) {
            if (index < passed + list.size) {
                return list[index - passed]
            }
            passed += list.size
        }
        throw IndexOutOfBoundsException()
    }

    public companion object {
        /**
         * This method returns a concatenated view of both lists.
         *
         * This means that the elements of both lists aren't copied,
         * however, if the lists are mutable, then this list will
         * also be updated (since we are only viewing the two lists).
         *
         * @param E The type of the elements in the list.
         * @param other The other list to concatenate with.
         * @return The concatenated view of the lists.
         */
        public fun <E> List<E>.concat(other: List<E>): List<E> {
            if (this is ConcatenatedList) {
                this.lists.add(other)
                return this
            }
            if (other is ConcatenatedList) {
                other.lists.add(0, other)
                return other
            }
            return ConcatenatedList(mutableListOf(this, other))
        }
    }
}