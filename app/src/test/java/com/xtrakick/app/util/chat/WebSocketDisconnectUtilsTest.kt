package com.xtrakick.app.util.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSocketDisconnectUtilsTest {

    @Test
    fun detectsHostResolutionFailures() {
        assertTrue(WebSocketDisconnectUtils.isHostResolutionFailure("java.net.UnknownHostException: Unable to resolve host 'ws.kick.com'"))
        assertTrue(WebSocketDisconnectUtils.isHostResolutionFailure("No address associated with hostname"))
        assertTrue(WebSocketDisconnectUtils.isHostResolutionFailure("Unable to resolve host 'ws.kick.com'"))
    }

    @Test
    fun ignoresOtherDisconnectReasons() {
        assertFalse(WebSocketDisconnectUtils.isHostResolutionFailure(null))
        assertFalse(WebSocketDisconnectUtils.isHostResolutionFailure(""))
        assertFalse(WebSocketDisconnectUtils.isHostResolutionFailure("java.net.SocketTimeoutException: timeout"))
    }

    @Test
    fun detectsTransientGatewayFailures() {
        assertTrue(WebSocketDisconnectUtils.isTransientGatewayFailure("HTTP/1.1 502 Bad Gateway"))
        assertTrue(WebSocketDisconnectUtils.isTransientGatewayFailure("HTTP/1.1 503 Service Unavailable"))
        assertTrue(WebSocketDisconnectUtils.isTransientGatewayFailure("HTTP/1.1 504 Gateway Time-out"))
        assertTrue(WebSocketDisconnectUtils.isTransientGatewayFailure("504 Gateway Timeout"))
    }

    @Test
    fun ignoresNonGatewayFailures() {
        assertFalse(WebSocketDisconnectUtils.isTransientGatewayFailure(null))
        assertFalse(WebSocketDisconnectUtils.isTransientGatewayFailure(""))
        assertFalse(WebSocketDisconnectUtils.isTransientGatewayFailure("HTTP/1.1 401 Unauthorized"))
        assertFalse(WebSocketDisconnectUtils.isTransientGatewayFailure("java.net.UnknownHostException"))
    }

    @Test
    fun detectsTransportErrors() {
        assertTrue(WebSocketDisconnectUtils.isTransportError("java.net.SocketException: Software caused connection abort"))
        assertTrue(WebSocketDisconnectUtils.isTransportError("java.net.SocketTimeoutException: timeout"))
        assertTrue(WebSocketDisconnectUtils.isTransportError("java.net.ConnectException: Connection refused"))
        assertTrue(WebSocketDisconnectUtils.isTransportError("recvfrom failed: ECONNRESET (Connection reset by peer)"))
        assertTrue(WebSocketDisconnectUtils.isTransportError("sendto failed: EPIPE (Broken pipe)"))
    }

    @Test
    fun ignoresNonTransportErrors() {
        assertFalse(WebSocketDisconnectUtils.isTransportError(null))
        assertFalse(WebSocketDisconnectUtils.isTransportError(""))
        assertFalse(WebSocketDisconnectUtils.isTransportError("HTTP/1.1 502 Bad Gateway"))
        assertFalse(WebSocketDisconnectUtils.isTransportError("java.net.UnknownHostException"))
        assertFalse(WebSocketDisconnectUtils.isTransportError("End of input at character 0"))
    }

    @Test
    fun normalizesEphemeralPortsInDisconnectMessages() {
        val msg1 = "failed to connect to host/2606:4700::1 (port 443) from /2001:db8::1 (port 45786) after 20000ms"
        val msg2 = "failed to connect to host/2606:4700::1 (port 443) from /2001:db8::1 (port 38870) after 20000ms"
        val norm1 = WebSocketDisconnectUtils.normalizeDisconnectReason(msg1)
        val norm2 = WebSocketDisconnectUtils.normalizeDisconnectReason(msg2)

        org.junit.Assert.assertEquals(norm1, norm2)
        org.junit.Assert.assertEquals(
            "failed to connect to host/2606:4700::1 (port ***) from /2001:db8::1 (port ***) after 20000ms",
            norm1
        )
    }
}
