package tech.pegasys.heku.util.collections

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*

interface FMap<TKey, TValue> : Map<TKey, TValue> {

    data class Change<TKey, TValue>(
        val key: TKey,
        val valueAdded: TValue?,
        val valueRemoved: TValue?
    ) {
        val isAdded get() = valueAdded != null && valueRemoved == null
        val isRemoved get() = valueAdded == null && valueRemoved != null
        val isUpdated get() = valueAdded != null && valueRemoved != null

        init {
            require(valueAdded != null || valueRemoved != null)
        }

        fun <R> map(mapper: (TValue) -> R): Change<TKey, R> =
            Change(key, valueAdded?.let { mapper(it) }, valueRemoved?.let { mapper(it) })
    }

    data class Update<TKey, TValue>(
        val changes: List<Change<TKey, TValue>>
    ) {
        fun <R> map(mapper: (TValue) -> R): Update<TKey, R> = Update(changes.map { it.map(mapper) })
    }

    fun getUpdates(): Flow<Update<TKey, TValue>>
    fun getChanges(): Flow<Change<TKey, TValue>> =
        getUpdates()
            .flatMapConcat {
                it.changes.asFlow()
            }
}

class UpdateFlowFMap<TKey, TValue>(
    changes: Flow<FMap.Change<TKey, TValue>>,
    backingMap: MutableMap<TKey, TValue> = mutableMapOf(),
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : FMap<TKey, TValue>, Map<TKey, TValue> by backingMap {

    private val updatesFlow: Flow<FMap.Update<TKey, TValue>> = changes
        .mapNotNull { change ->
            when {
                change.valueAdded != null -> {
                    val oldVal = backingMap.put(change.key, change.valueAdded)
                    when {
                        oldVal == null ->
                            change
                        oldVal != change.valueAdded -> {
                            val va: TValue = change.valueAdded
                            FMap.Change(change.key, va, oldVal)
                        }
                        else -> null
                    }
                }
                change.isRemoved -> {
                    if (backingMap.remove(change.key) != null)
                        change
                    else
                        null
                }
                else -> throw IllegalStateException("Impossible")
            }

        }.map {
            FMap.Update(listOf(it))
        }.shareIn(scope, SharingStarted.Eagerly)

    override fun getUpdates(): Flow<FMap.Update<TKey, TValue>> = updatesFlow

    companion object {
        fun <T, TKey, TValue> createFromUpdates(
            updateFlow: Flow<FSet.Update<T>>,
            keyFunc: (T) -> TKey,
            valueFunc: (T) -> TValue
        ): FMap<TKey, TValue> {
            val changesFlow = updateFlow
                .flatMapConcat {
                    val addChanges = it.added.map {
                        FMap.Change(keyFunc(it), valueFunc(it), null)
                    }
                    val removeChanges = it.removed.map {
                        FMap.Change(keyFunc(it), null, valueFunc(it))
                    }
                    (addChanges + removeChanges).asFlow()
                }
            return UpdateFlowFMap(changesFlow)
        }

        fun <TKey, TValue> createFromAddRemoveFlows(
            addUpdateDuplicatesFlow: Flow<TValue>,
            removeFlow: Flow<TValue>,
            keyFunc: (TValue) -> TKey
        ): FMap<TKey, TValue> {
            val changesFlow = flowOf(
                addUpdateDuplicatesFlow.map { FMap.Change(keyFunc(it), it, null) },
                removeFlow.map { FMap.Change(keyFunc(it), null, it) }
            ).flattenMerge()
            return UpdateFlowFMap(changesFlow)
        }
    }
}