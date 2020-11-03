package io.perfometer.runner

import io.perfometer.dsl.HttpStep
import io.perfometer.dsl.ParallelStep
import io.perfometer.dsl.PauseStep
import io.perfometer.dsl.RequestStep
import io.perfometer.http.client.HttpClient
import io.perfometer.statistics.PauseStatistics
import io.perfometer.statistics.ScenarioSummary
import kotlinx.coroutines.*
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinDuration

internal class CoroutinesScenarioRunner(
    httpClient: HttpClient,
) : BaseScenarioRunner(httpClient) {

    private val parallelJobs = ConcurrentLinkedDeque<Deferred<Unit>>()

    @ExperimentalTime
    override fun runUsers(
        userCount: Int,
        duration: Duration,
        action: suspend () -> Unit,
    ): ScenarioSummary {
        runBlocking(Dispatchers.Default) {
            (1..userCount).map {
                launch {
                    withTimeout(duration.toKotlinDuration()) {
                        while (isActive) action()
                    }
                }
            }
        }
        return runBlocking(Dispatchers.Default) {
            withTimeoutOrNull(duration.toKotlinDuration()) {
                awaitAll(*parallelJobs.toTypedArray())
            }
            statistics.finish()
        }
    }

    override suspend fun runStep(step: HttpStep) {
        when (step) {
            is RequestStep -> executeHttp(step)
            is PauseStep -> pauseFor(step.duration)
            is ParallelStep -> runParallel(step)
        }
    }

    private suspend fun runParallel(step: ParallelStep) {
        GlobalScope.launch {
            parallelJobs.add(async {
                step.action()
            })
        }
    }

    private suspend fun pauseFor(duration: Duration) {
        delay(duration.toMillis())
        statistics.gather(PauseStatistics(duration))
    }
}
