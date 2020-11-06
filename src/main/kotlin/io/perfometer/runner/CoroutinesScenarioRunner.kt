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

    // todo @ttarczynski - consider moving this to a separate class
    private val parallelJobs = ConcurrentLinkedDeque<Job>()

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
        return statistics.finish()
    }

    override suspend fun runStep(step: HttpStep) {
        when (step) {
            is RequestStep -> executeHttp(step)
            is PauseStep -> pauseFor(step.duration)
            is ParallelStep -> runParallel(step)
        }
    }

    override suspend fun runStepAsync(step: HttpStep) {
        parallelJobs.add(GlobalScope.launch { runStep(step) })
    }

    private suspend fun runParallel(step: ParallelStep) {
        step.action()
        parallelJobs.joinAll().also { parallelJobs.clear() }
    }

    private suspend fun pauseFor(duration: Duration) {
        delay(duration.toMillis())
        statistics.gather(PauseStatistics(duration))
    }
}
