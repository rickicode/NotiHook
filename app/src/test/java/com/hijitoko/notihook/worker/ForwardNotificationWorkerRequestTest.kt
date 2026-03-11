package com.hijitoko.notihook.worker

import com.hijitoko.notihook.data.HttpMethod
import com.hijitoko.notihook.data.PayloadType
import okhttp3.RequestBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForwardNotificationWorkerRequestTest {

    @Test
    fun `post json uses application json body`() {
        val spec = ForwardNotificationWorker.buildRequestSpec(
            url = "https://example.com/hook",
            method = HttpMethod.POST,
            payloadType = PayloadType.JSON,
            payload = linkedMapOf("apikey" to "hijilabs", "title" to "Hello"),
            userAgent = "NotiHook/1.0"
        )

        val request = spec.toRequest()

        assertEquals("POST", request.method)
        assertEquals("application/json; charset=utf-8", request.body!!.contentType().toString())
        assertEquals("NotiHook/1.0", request.header("User-Agent"))
        assertTrue(bodyToString(request.body!!).contains("\"apikey\":\"hijilabs\""))
        assertTrue(bodyToString(request.body!!).contains("\"title\":\"Hello\""))
    }

    @Test
    fun `post form uses form encoded body`() {
        val spec = ForwardNotificationWorker.buildRequestSpec(
            url = "https://example.com/hook",
            method = HttpMethod.POST,
            payloadType = PayloadType.FORM,
            payload = linkedMapOf("apikey" to "hijilabs", "title" to "Hello World"),
            userAgent = "NotiHook/1.0"
        )

        val request = spec.toRequest()

        assertEquals("POST", request.method)
        assertEquals("application/x-www-form-urlencoded", request.body!!.contentType().toString())
        val body = bodyToString(request.body!!)
        assertTrue(body.contains("apikey=hijilabs"))
        assertTrue(body.contains("title=Hello%20World") || body.contains("title=Hello+World"))
    }

    @Test
    fun `blank user agent falls back to default user agent`() {
        val resolved = ForwardNotificationWorker.resolveUserAgent("")
        assertEquals("NotiHook/1.0", resolved)
    }

    @Test
    fun `post json keeps explicit content type header`() {
        val request = ForwardNotificationWorker.buildRequestSpec(
            url = "https://example.com/hook",
            method = HttpMethod.POST,
            payloadType = PayloadType.JSON,
            payload = linkedMapOf("apikey" to "hijilabs"),
            userAgent = ""
        ).toRequest()

        assertEquals("application/json; charset=utf-8", request.header("Content-Type"))
        assertEquals("NotiHook/1.0", request.header("User-Agent"))
    }

    private fun bodyToString(body: RequestBody): String {
        val buffer = Buffer()
        body.writeTo(buffer)
        return buffer.readUtf8()
    }
}
