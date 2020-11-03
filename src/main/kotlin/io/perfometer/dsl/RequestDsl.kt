package io.perfometer.dsl

import io.perfometer.http.HttpMethod
import io.perfometer.http.HttpRequest
import io.perfometer.http.HttpResponse
import java.net.URL

class RequestDsl(
    initialHeaders: Map<String, String>,
    private val url: URL,
    private val method: HttpMethod,
) {
    private var name: String? = null
    private var path: String = ""
    private val headers = initialHeaders.toMutableMap()
    private val params = mutableListOf<HttpParam>()
    private var body: ByteArray = ByteArray(0)
    private var consumer: (HttpResponse) -> Unit = {}

    fun name(name: String) {
        this.name = name
    }

    fun path(path: String) {
        this.path = path
    }

    fun body(body: ByteArray) {
        this.body = body
    }

    fun headers(vararg headers: HttpHeader) {
        this.headers.putAll(headers)
    }

    fun params(vararg params: HttpParam) {
        this.params.addAll(params)
    }

    fun consume(consumer: (HttpResponse) -> Unit) {
        this.consumer = consumer
    }

    private fun pathWithParams(): String {
        return path + paramsToString()
    }

    private fun paramsToString(): String {
        return if (this.params.isNotEmpty())
            this.params.joinToString("&", "?") { "${it.first}=${it.second}" }
        else ""
    }

    fun build() =
        HttpRequest(name ?: "$method $path", method, url, pathWithParams(), headers, body, consumer)
}
