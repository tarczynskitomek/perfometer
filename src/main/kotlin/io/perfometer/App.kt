package io.perfometer

import io.perfometer.dsl.HttpHeader
import io.perfometer.dsl.HttpParam
import io.perfometer.dsl.scenario
import io.perfometer.http.client.SimpleHttpClient
import io.perfometer.runner.DefaultScenarioRunner
import io.perfometer.statistics.printer.StdOutStatisticsPrinter


fun main() {

    DefaultScenarioRunner(SimpleHttpClient(true), StdOutStatisticsPrinter())
            .run(scenario("https", "flamingo-test.ext.e-point.pl", 443) {
                basicAuth("user", "password")
                get().path { "/rejestracja" }
                get().path { "/rejestracja/static/css/2.1566cbe7.chunk.css" }
                get().path { "/rejestracja/static/css/main.19c2a943.chunk.css" }
                get().path { "/rejestracja/static/js/2.41d425fe.chunk.js" }
                get().path { "/rejestracja/static/js/main.553b7257.chunk.js" }
                get().path { "/rejestracja/api/status" }
//                pause(Duration.ofSeconds(10))
                val fundsResponse = get().path { "/rejestracja/api/funds" }.response
                get().path { "/rejestracja/api/fund-types" }
                get().path { "/rejestracja/api/risks" }
                get().path { "/rejestracja/api/horizons" }
                get().path { "/rejestracja/api/umbrellas" }
                get().path { "/rejestracja/api/currentLimit" }
                get().path { "/rejestracja/api/currentLimit" }
                        .header { HttpHeader("X-Perfometer", "true") }
                        .param { HttpParam("first", "aaa") }
                        .param { HttpParam("second", "bbb") }

                get().path { "rejestracja/api/potential-profit/" }.param { HttpParam("fundId", fundsResponse.body) }
                // Provide your scenario here
            })
}
