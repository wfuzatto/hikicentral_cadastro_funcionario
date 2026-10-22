package br.com.acquavale.bridge;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
public final class TokenCrypto {
 private TokenCrypto(){}
 public static String generate(UUID id) {
   byte[] value=new byte[32];new SecureRandom().nextBytes(value);
   return id+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(value);
 }
 public static String hash(byte[] value) {
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}
  catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
 public static String hash(String value){return hash(value.getBytes(StandardCharsets.UTF_8));}
 public static boolean matches(String token,String hex) {
  return hex!=null && hex.length()==64 &&
   MessageDigest.isEqual(hash(token).getBytes(StandardCharsets.US_ASCII),hex.getBytes(StandardCharsets.US_ASCII));
 }
}
