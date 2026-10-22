package br.com.acquavale.bridge;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ArtemisClientTest {
 @Test void canonicalSignatureKeepsLegacyWireFormat() {
  assertEquals("POST\n*/*\napplication/json\nx-ca-key:key-1\nx-ca-timestamp:1000\n/artemis/api/resource/v1/org/rootOrg",
   ArtemisClient.canonical("/artemis/api/resource/v1/org/rootOrg","key-1","1000"));
  String a=ArtemisClient.signature("/artemis/api/resource/v1/org/rootOrg","key-1","secret","1000");
  String b=ArtemisClient.signature("/artemis/api/resource/v1/org/rootOrg","key-1","secret","1000");
  assertEquals(a,b);
  assertNotEquals(a,ArtemisClient.signature("/artemis/api/resource/v1/org/rootOrg","key-1","different","1000"));
 }
 @Test void sha256TokenDoesNotStoreRawValue(){
  String token=TokenCrypto.generate(java.util.UUID.randomUUID());
  assertTrue(token.length()>70);
  assertTrue(TokenCrypto.matches(token,TokenCrypto.hash(token)));
  assertFalse(TokenCrypto.matches(token+"X",TokenCrypto.hash(token)));
  assertFalse(TokenCrypto.hash(token).contains(token));
 }
}
