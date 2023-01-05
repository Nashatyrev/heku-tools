package tech.pegasys.heku.util.collections

import java.util.*

class LimitedList<C>(
    val maxSize: Int,
    val onDropCallback: ((C) -> Unit) = { }
) : LinkedList<C>() {


    override fun add(element: C): Boolean {
        val ret = super.add(element)
        while (size > maxSize) shrink()
        return ret
    }

    private fun shrink() {
        onDropCallback.invoke(removeFirst())
    }
}
