package tech.pegasys.heku.util.collections

import kotlinx.coroutines.flow.*

interface FSet<T> : Set<T> {

    data class Update<T>(
        val removed: Set<T>,
        val added: Set<T>
    ) {
        fun <R> map(mapper: (T) -> R): Update<R> =
            Update(removed.map(mapper).toSet(), added.map(mapper).toSet())

        fun filter(predicate: (T) -> Boolean): Update<T> =
            Update(removed.filter(predicate).toSet(), removed.filter(predicate).toSet())

        fun nonEmptyOrNull() = if (removed.isEmpty() && added.isEmpty()) null else this

        companion object {
            fun <T> createAdded(addedElem: T) = Update(emptySet(), setOf(addedElem))
            fun <T> createRemoved(removedElem: T) = Update(setOf(removedElem), emptySet())
        }
    }

    fun getUpdates(): Flow<Update<T>>
    fun addedFlow() = getUpdates().flatMapConcat { it.added.asFlow() }

//    data class SnapshotUpdate<T>(
//        val oldValue: Set<T>,
//        val newValue: Set<T>
//    )
//
//    fun getSnapshotUpdates(): Flow<SnapshotUpdate<T>> =
//        flow {
//            val updateFlow = getUpdates()
//            val curSet = mutableSetOf<T>()
//            var prevSnapshot = setOf<T>()
//            updateFlow.collect { update ->
//                curSet -= update.removed
//                curSet += update.added
//                val newSnapshot = curSet.toSet()
//                emit(SnapshotUpdate(prevSnapshot, newSnapshot))
//                prevSnapshot = newSnapshot
//            }
//        }

    // TODO now fMap and fFilter should work with mapped classes implementing equals/hashCode only
    fun <R> fMap(mapper: (T) -> R): FSet<R> =
        createFromUpdates(getUpdates().map {
            it.map(mapper)
        })

    fun fFilter(predicate: (T) -> Boolean): FSet<T> =
        createFromUpdates(getUpdates().mapNotNull {
            it.filter(predicate).nonEmptyOrNull()
        })

    companion object {
        fun <T> createFromUpdates(updateFlow: Flow<Update<T>>): FSet<T> =
            FMapSet(UpdateFlowFMap.createFromUpdates(updateFlow, { it }, { }))

    }
}

class FMapSet<T>(
    val map: FMap<T, Unit>
) : FSet<T>, Set<T> by map.keys {

    override fun getUpdates(): Flow<FSet.Update<T>> =
        map.getUpdates()
            .map { mapUpdate ->
                val removed = mapUpdate.changes
                    .filter { it.isRemoved }
                    .map { it.key }
                    .toSet()
                val added = mapUpdate.changes
                    .filter { it.isAdded }
                    .map { it.key }
                    .toSet()
                FSet.Update(removed, added)
            }
}