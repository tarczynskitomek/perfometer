package io.perfometer.runner

import io.perfometer.dsl.*
import io.perfometer.http.client.HttpClient
import io.perfometer.internal.helper.decorateInterruptable
import io.perfometer.internal.helper.decorateSuspendingInterruptable
import io.perfometer.statistics.PauseStatistics
import io.perfometer.statistics.ScenarioSummary
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.util.concurrent.*

internal class ThreadPoolScenarioRunner(
    httpClient: HttpClient,
) : BaseScenarioRunner(httpClient) {

    private val parallelJobs = ThreadLocal.withInitial { ConcurrentLinkedDeque<CompletableFuture<Void>>() }
    private val parallelJobsExecutor: ExecutorService = Executors.newCachedThreadPool()

    override fun runUsers(
        userCount: Int,
        duration: Duration,
        action: suspend () -> Unit,
    ): ScenarioSummary {
        return Executors.newFixedThreadPool(userCount).let { executor ->
            runUsersInternal(userCount, executor, action).let { usersFeature ->
                timeoutExecutors(duration, executor)
                usersFeature.join()
                statistics.finish()
            }
        }
    }

    override suspend fun runStep(step: HttpStep) {
        when (step) {
            is RequestStep -> executeHttp(step)
            is PauseStep -> pauseFor(step.duration)
            is ParallelStep -> runParallel(step)
        }
    }

    override suspend fun runStepAsync(step: HttpStep) {
        parallelJobs.get().add(
            CompletableFuture.runAsync(
                { runBlocking { runStep(step) } }, parallelJobsExecutor
            )
        )
    }

    private fun runUsersInternal(
        userCount: Int,
        scenarioExecutor: ExecutorService,
        action: suspend () -> Unit,
    ): CompletableFuture<Void> {
        return CompletableFuture.allOf(
            *(0 until userCount)
                .map { CompletableFuture.runAsync({ runAction(action) }, scenarioExecutor) }
                .toTypedArray())
    }

    private fun timeoutExecutors(
        duration: Duration,
        scenarioExecutor: ExecutorService,
    ) {
        Executors.newSingleThreadScheduledExecutor().schedule({
            scenarioExecutor.shutdownNow()
            parallelJobsExecutor.shutdownNow()
        }, duration.toNanos(), TimeUnit.NANOSECONDS)
    }

    private fun runParallel(step: ParallelStep) {
        runBlocking {
            step.action()
        }
        CompletableFuture.allOf(*parallelJobs.get().toTypedArray()).join()
    }

    private fun runAction(action: suspend () -> Unit) = decorateInterruptable {
        runBlocking {
            while (!Thread.currentThread().isInterrupted) {
                decorateSuspendingInterruptable(action)
            }
        }
    }

    private fun pauseFor(duration: Duration) {
        Thread.sleep(duration.toMillis())
        statistics.gather(PauseStatistics(duration))
    }

}
