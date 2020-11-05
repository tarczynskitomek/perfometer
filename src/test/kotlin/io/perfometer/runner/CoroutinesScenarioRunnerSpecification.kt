package io.perfometer.runner;

import io.perfometer.dsl.scenario
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.time.Duration

class CoroutinesScenarioRunnerSpecification : ScenarioRunnerSpecification() {

    override val runner: ScenarioRunner = CoroutinesScenarioRunner(httpClient)

    @Test
    fun `should timeout scenario event if there are parallel tasks still running`() {
        assertDoesNotThrow {
            scenario("https://perfometer.io") {
                get { path("/") }
                parallel {
                    pause(Duration.ofSeconds(1500))
                }
            }.runner(runner).run(1, Duration.ofMillis(100))
        }
    }
}
