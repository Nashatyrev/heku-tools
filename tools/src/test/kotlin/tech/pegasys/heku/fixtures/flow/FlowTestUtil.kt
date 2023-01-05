package tech.pegasys.heku.util.flow

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile

suspend fun MutableSharedFlow<*>.awaitForSubscribers(numberOfSubscribers: Int) {
        this@awaitForSubscribers.subscriptionCount
            .takeWhile { it < numberOfSubscribers }
            .collect()
    }
