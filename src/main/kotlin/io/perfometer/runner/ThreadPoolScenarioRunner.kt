package io.perfometer.runner

import io.perfometer.dsl.HttpStep
import io.perfometer.dsl.ParallelStep
import io.perfometer.dsl.PauseStep
import io.perfometer.dsl.RequestStep
import io.perfometer.http.client.HttpClient
import io.perfometer.statistics.PauseStatistics
import io.perfometer.statistics.ScenarioSummary
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class ThreadPoolScenarioRunner(
    httpClient: HttpClient,
) : BaseScenarioRunner(httpClient) {

    override fun runUsers(
        userCount: Int,
        duration: Duration,
        action: suspend () -> Unit,
    ): ScenarioSummary {
        val scenarioExecutor = Executors.newFixedThreadPool(userCount)

        val future = CompletableFuture.allOf(
            *(0 until userCount)
                .map { CompletableFuture.runAsync({ runAction(action) }, scenarioExecutor) }
                .toTypedArray())

        Executors.newSingleThreadScheduledExecutor().schedule({
            scenarioExecutor.shutdownNow()
        }, duration.toNanos(), TimeUnit.NANOSECONDS)

        future.join()
        return statistics.finish()
    }

    override suspend fun runStep(step: HttpStep) {
        when (step) {
            is RequestStep -> executeHttp(step)
            is PauseStep -> pauseFor(step.duration)
            is ParallelStep -> TODO()
        }
    }

    override suspend fun runStepAsync(step: HttpStep) {
        TODO("Not yet implemented")
    }

    private fun runAction(action: suspend () -> Unit) {
        runBlocking {
            while (!Thread.currentThread().isInterrupted) {
                try {
                    action()
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
    }

    private fun pauseFor(duration: Duration) {
        Thread.sleep(duration.toMillis())
        statistics.gather(PauseStatistics(duration))
    }
}
