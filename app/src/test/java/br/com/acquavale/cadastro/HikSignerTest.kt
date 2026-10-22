package br.com.acquavale.cadastro

import org.junit.Assert.assertEquals
import org.junit.Test

class HikSignerTest {
    @Test fun canonicalSignatureHasExpectedFieldsInOrder() {
        assertEquals(
            "POST\n*/*\napplication/json\nx-ca-key:key-1\nx-ca-timestamp:1000\n/artemis/api/resource/v1/org/rootOrg",
            HikSigner.canonical("/artemis/api/resource/v1/org/rootOrg", "key-1", "1000")
        )
    }
}
