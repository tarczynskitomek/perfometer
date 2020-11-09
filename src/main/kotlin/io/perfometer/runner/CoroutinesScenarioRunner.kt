package io.perfometer.runner

import io.perfometer.dsl.*
import io.perfometer.http.client.HttpClient
import io.perfometer.statistics.PauseStatistics
import io.perfometer.statistics.ScenarioSummary
import kotlinx.coroutines.*
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.coroutines.*
import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinDuration

internal class CoroutinesScenarioRunner(
    httpClient: HttpClient,
) : BaseScenarioRunner(httpClient) {

    private lateinit var duration: Duration

    @ExperimentalTime
    override fun runUsers(
        userCount: Int,
        duration: Duration,
        action: suspend () -> Unit,
    ): ScenarioSummary {
        this.duration = duration
        runBlocking(Dispatchers.Default) {
            (1..userCount).map {
                launch(CoroutineParallelJobs()) {
                    withTimeout(duration.toKotlinDuration()) {
                        while (isActive) action()
                    }
                }
            }
        }
        return statistics.finish()
    }

    @ExperimentalTime
    override suspend fun runStep(step: HttpStep) {
        when (step) {
            is RequestStep -> executeHttp(step)
            is PauseStep -> pauseFor(step.duration)
            is ParallelStep -> runParallel(step)
        }
    }

    @ExperimentalTime
    override suspend fun runStepAsync(step: HttpStep) {
        parallelJobs().add(GlobalScope.launch {
            runStep(step)
        })
    }

    @ExperimentalTime
    private suspend fun runParallel(step: ParallelStep) {
        withTimeoutOrNull(duration.toKotlinDuration()) {
            step.action()
            parallelJobs().joinAll()
        }
    }

    private suspend fun pauseFor(duration: Duration) {
        delay(duration.toMillis())
        statistics.gather(PauseStatistics(duration))
    }

    private suspend fun parallelJobs(): ConcurrentLinkedDeque<Job> {
        return coroutineContext[CoroutineParallelJobs.Key]?.jobs ?: throw IllegalStateException()
    }

    private data class CoroutineParallelJobs(
        val jobs: ConcurrentLinkedDeque<Job> = ConcurrentLinkedDeque<Job>()
    ) : AbstractCoroutineContextElement(CoroutineParallelJobs) {
        companion object Key : CoroutineContext.Key<CoroutineParallelJobs>
    }

}
