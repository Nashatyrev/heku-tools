package tech.pegasys.heku.util.flow

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter

class FlowKeyMerger<TKey> {

    // TODO incorrect logic is implemented here
    companion object {

        fun <T1, T2, TKey> merge2(
            flow1: Flow<T1>,
            keyExtractor1: (T1) -> TKey,
            flow2: Flow<T2>,
            keyExtractor2: (T2) -> TKey,
        ): Flow<Pair<T1, T2>> {
            return flow1.combine(flow2) { o1, o2 -> o1 to o2}
                .filter { keyExtractor1(it.first) == keyExtractor2(it.second) }
        }

        fun <T1, T2, T3, TKey> merge3(
            flow1: Flow<T1>,
            keyExtractor1: (T1) -> TKey,
            flow2: Flow<T2>,
            keyExtractor2: (T2) -> TKey,
            flow3: Flow<T3>,
            keyExtractor3: (T3) -> TKey,
        ): Flow<Triple<T1, T2, T3>> {
            return flow1
                .combine(flow2) { o1, o2 -> o1 to o2}
                .combine(flow3) { p1, o2 -> Triple(p1.first, p1.second, o2) }
                .filter { keyExtractor1(it.first) == keyExtractor2(it.second) == keyExtractor3(it.third) }
        }
    }
}