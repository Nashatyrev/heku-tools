package tech.pegasys.heku.util.beacon

import io.libp2p.etc.types.forward
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import tech.pegasys.teku.infrastructure.async.SafeFuture
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState
import tech.pegasys.teku.storage.client.CombinedChainDataClient
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Creates a [Flow] of latest head [BeaconState]s by polling [CombinedChainDataClient]
 */
class BestBeaconStatePoller(
    private val dataClient: CombinedChainDataClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val pollPeriod: Duration = 500.milliseconds,
    private val initialPollPeriod: Duration = 200.milliseconds
) {
    val initialBestState: SafeFuture<BeaconState> = run {
        val maybeStateFuture = dataClient.bestState
        if (maybeStateFuture.isPresent) {
            maybeStateFuture.get()
        } else {
            val ret = SafeFuture<BeaconState>()
            scope.launch {
                while (true) {
                    val maybeStateFuture = dataClient.bestState
                    if (maybeStateFuture.isPresent) {
                        maybeStateFuture.get().forward(ret)
                        break
                    }
                    delay(initialPollPeriod)
                }
            }
            ret
        }
    }

    val bestStateFlow = initialBestState
        .thenApply { initialState ->
            flow<BeaconState> {
                var lastState: BeaconState? = null
                while (true) {
                    val stateFuture = dataClient.bestState.orElseThrow()
                    val state = stateFuture.await()
                    if (lastState == null || lastState.hashTreeRoot() != state.hashTreeRoot()) {
                        emit(state)
                        lastState = state
                    }

                    delay(pollPeriod)
                }
            }
                .catch { it.printStackTrace() }
                .stateIn(scope, SharingStarted.Eagerly, initialState)
        }
}