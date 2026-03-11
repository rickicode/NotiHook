package com.hijitoko.notihook.forward

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForwardPayloadBuilderTest {

    @Test
    fun `buildPayloadMap merges notification fields and additional values`() {
        val input = ForwardPayloadInput(
            title = "Bayar Transaksi QR Statis Merchan",
            text = "Transaksi pembayaran invoice",
            bigText = "Transaksi pembayaran invoice",
            subText = "",
            infoText = "",
            name = "bale merchant",
            pkg = "com.btn.btnmerchant"
        )

        val payload = ForwardPayloadBuilder.buildPayloadMap(
            input = input,
            additionalValues = mapOf("apikey" to "hijilabs", "source" to "notif")
        )

        assertEquals("hijilabs", payload["apikey"])
        assertEquals("Bayar Transaksi QR Statis Merchan", payload["title"])
        assertEquals("Transaksi pembayaran invoice", payload["text"])
        assertEquals("com.btn.btnmerchant", payload["pkg"])
        assertEquals("notif", payload["source"])
    }

    @Test
    fun `buildGetUrl appends encoded query params`() {
        val url = ForwardPayloadBuilder.buildGetUrl(
            baseUrl = "https://example.com/hook",
            params = mapOf(
                "title" to "Bayar Transaksi",
                "apikey" to "hijilabs"
            )
        )

        assertTrue(url.startsWith("https://example.com/hook?"))
        assertTrue(url.contains("apikey=hijilabs"))
        assertTrue(url.contains("title=Bayar+Transaksi"))
    }

    @Test
    fun `buildGetUrl keeps existing query parameters`() {
        val url = ForwardPayloadBuilder.buildGetUrl(
            baseUrl = "https://example.com/hook?channel=wa",
            params = mapOf("apikey" to "hijilabs")
        )

        assertTrue(url.contains("channel=wa"))
        assertTrue(url.contains("apikey=hijilabs"))
    }
}
