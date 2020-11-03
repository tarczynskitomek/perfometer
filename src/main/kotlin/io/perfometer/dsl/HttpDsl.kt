package io.perfometer.dsl

import io.perfometer.http.HttpHeaders
import io.perfometer.http.HttpMethod
import io.perfometer.http.HttpRequest
import io.perfometer.http.client.KtorHttpClient
import io.perfometer.internal.helper.toReadableString
import io.perfometer.internal.helper.toUrl
import io.perfometer.runner.CoroutinesScenarioRunner
import io.perfometer.runner.ScenarioRunner
import io.perfometer.statistics.ScenarioSummary
import io.perfometer.statistics.consumer.Output
import io.perfometer.statistics.consumer.Output.STDOUT
import io.perfometer.statistics.consumer.consumeStatistics
import java.net.URL
import java.time.Duration
import java.util.*

sealed class HttpStep
data class RequestStep(val request: HttpRequest) : HttpStep()
data class PauseStep(val duration: Duration) : HttpStep()
data class ParallelStep(val action: suspend () -> Unit) : HttpStep()

typealias HttpHeader = Pair<String, String>
typealias HttpParam = Pair<String, String>

class HttpDsl(
    private val baseURL: URL,
    private val scenarioRunner: ScenarioRunner,
) {
    private val headers = mutableMapOf<String, String>()

    fun headers(vararg headers: HttpHeader) {
        this.headers.putAll(headers)
    }

    fun basicAuth(user: String, password: String) {
        val credentialsEncoded = Base64.getEncoder().encodeToString("$user:$password".toByteArray())
        headers(HttpHeaders.AUTHORIZATION to "Basic $credentialsEncoded")
    }

    suspend fun parallel(
        builder: suspend HttpDsl.() -> Unit,
    ) {
        val dsl = HttpDsl(baseURL, scenarioRunner)
        scenarioRunner.runStep(ParallelStep { builder(dsl) })
    }

    suspend fun get(urlString: String? = null, builder: RequestDsl.() -> Unit) =
        request(HttpMethod.GET, urlString, builder)

    suspend fun post(urlString: String? = null, builder: RequestDsl.() -> Unit) =
        request(HttpMethod.POST, urlString, builder)

    suspend fun put(urlString: String? = null, builder: RequestDsl.() -> Unit) =
        request(HttpMethod.PUT, urlString, builder)

    suspend fun delete(urlString: String? = null, builder: RequestDsl.() -> Unit) =
        request(HttpMethod.DELETE, urlString, builder)

    suspend fun patch(urlString: String? = null, builder: RequestDsl.() -> Unit) =
        request(HttpMethod.PATCH, urlString, builder)

    suspend fun pause(duration: Duration) {
        scenarioRunner.runStep(PauseStep(duration))
    }

    private suspend fun request(
        httpMethod: HttpMethod,
        urlString: String?,
        builder: RequestDsl.() -> Unit
    ) {
        val requestUrl = urlString?.toUrl() ?: baseURL
        val request = RequestDsl(headers, requestUrl, httpMethod).apply(builder).build()
        scenarioRunner.runStep(RequestStep(request))
    }
}

class Scenario(
    private val baseURL: URL,
    private val builder: suspend HttpDsl.() -> Unit,
) {

    private var runner: ScenarioRunner = CoroutinesScenarioRunner(KtorHttpClient())

    fun runner(runner: ScenarioRunner): Scenario {
        this.runner = runner
        return this
    }

    fun run(
        userCount: Int,
        duration: Duration,
        vararg outputTo: Output = arrayOf(STDOUT)
    ): ScenarioSummary {
        println("Running scenario for $userCount users and ${duration.toReadableString()} time")
        return runner
            .runUsers(userCount, duration) { builder(HttpDsl(baseURL, runner)) }
            .also { consumeStatistics(it, *outputTo) }
    }
}

fun scenario(
    baseUrlString: String,
    builder: suspend HttpDsl.() -> Unit
) = Scenario(baseUrlString.toUrl(), builder)
