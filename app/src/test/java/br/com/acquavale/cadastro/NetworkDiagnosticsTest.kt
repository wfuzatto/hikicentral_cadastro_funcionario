package br.com.acquavale.cadastro

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException

class NetworkDiagnosticsTest {
    @Test fun addressRequiresHostAndValidPort() {
        assertTrue(NetworkDiagnostics.validAddress("608905397dde.sn.mynetname.net", 37443))
        assertFalse(NetworkDiagnostics.validAddress("https://example.com", 443))
        assertFalse(NetworkDiagnostics.validAddress("example.com", 0))
    }

    @Test fun diagnosticsClassifiesTransportErrors() {
        assertTrue(NetworkDiagnostics.networkHint(ConnectException()).contains("TCP"))
        assertTrue(NetworkDiagnostics.networkHint(SocketTimeoutException()).contains("Tempo"))
    }
}
