package io.perfometer.runner

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.perfometer.dsl.scenario
import io.perfometer.http.HttpMethod
import io.perfometer.http.HttpRequest
import io.perfometer.http.HttpResponse
import io.perfometer.http.HttpStatus
import io.perfometer.http.client.HttpClient
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.time.Duration

class CoroutinesScenarioRunnerSpecification {

    private val requests = mutableListOf<HttpRequest>()

    private val runner = CoroutinesScenarioRunner(object : HttpClient {
        override suspend fun executeHttp(request: HttpRequest): HttpResponse {
            synchronized(this) {
                requests += request
            }
            return HttpResponse(HttpStatus(200), emptyMap())
        }
    })

    @Test
    fun `should execute single GET request for a single user`() {
        val summary = scenario("https://perfometer.io") {
            get {
                path("/")
            }
        }.runner(runner).run(1, Duration.ofMillis(100))

        requests.size shouldBeGreaterThan 0
        requests.map { it.url.toString() }.filter { it != "https://perfometer.io" } shouldHaveSize 0
        requests.map { it.method }.filter { it != HttpMethod.GET } shouldHaveSize 0
        requests.map { it.pathWithParams }.filter { it != "/" } shouldHaveSize 0

        summary.totalSummary.shouldNotBeNull()
            .requestCount shouldBeGreaterThan 0
    }

    @Test
    fun `should execute 8 requests total on two async users`() {
        val summary = scenario("http://perfometer.io") {
            get {
                path("/")
            }
            get {
                path("/")
            }
            delete {
                path("/delete")
            }
            delete {
                path("/delete")
            }
        }.runner(runner).run(2, Duration.ofMillis(100))

        requests.size shouldBeGreaterThan 8
        summary.totalSummary.shouldNotBeNull()
            .requestCount shouldBeGreaterThan 8
    }

    @Test
    fun `should pause for at least two seconds`() {
        val startTime = System.currentTimeMillis()

        val summary = scenario("https://perfometer.io") {
            pause(Duration.ofSeconds(2))
        }.runner(runner).run(1, Duration.ofMillis(2100))

        val diff = System.currentTimeMillis() - startTime
        diff shouldBeGreaterThanOrEqualTo 2000L
        summary.totalSummary.shouldBeNull()
    }

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
