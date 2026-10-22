package br.com.acquavale.bridge;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.cert.*;
import java.time.*;
import java.util.*;

@Component @Profile("!admin")
public final class ArtemisClient implements ArtemisGateway {
 private final String key,secret;private final URI base;private final HttpClient client;
 private final ObjectMapper mapper=new ObjectMapper();
 public ArtemisClient() {
  key=required("HIK_APP_KEY");secret=required("HIK_APP_SECRET");
  base=URI.create(required("HIK_BASE_URL"));
  if(!"https".equalsIgnoreCase(base.getScheme())||base.getHost()==null||
    (base.getRawPath()!=null&&!base.getRawPath().isEmpty()&&!base.getRawPath().equals("/")))
   throw new IllegalArgumentException("HIK_BASE_URL deve ser https://host:porta sem caminho.");
  try {
   String caPath=required("HIK_CA_PEM");
   KeyStore ks=KeyStore.getInstance(KeyStore.getDefaultType());ks.load(null,null);
   CertificateFactory factory=CertificateFactory.getInstance("X.509");
   try(InputStream in=Files.newInputStream(Path.of(caPath))){
    Collection<? extends java.security.cert.Certificate> certs=factory.generateCertificates(in);
    if(certs.isEmpty())throw new IllegalArgumentException("CA PEM vazia.");
    int idx=0;
    for(java.security.cert.Certificate cert:certs){
     X509Certificate x=(X509Certificate)cert;
     if(x.getBasicConstraints()<0)throw new IllegalArgumentException("HIK_CA_PEM deve conter CA, não certificado final.");
     x.checkValidity();ks.setCertificateEntry("ca-"+(idx++),x);
    }
   }
   TrustManagerFactory tmf=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
   tmf.init(ks);
   SSLContext ssl=SSLContext.getInstance("TLS");
   ssl.init(null,tmf.getTrustManagers(),null);
   SSLParameters params=new SSLParameters();
   params.setEndpointIdentificationAlgorithm("HTTPS");
   client=HttpClient.newBuilder().sslContext(ssl).sslParameters(params)
    .connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build();
  } catch(Exception e) {throw new IllegalStateException("Não foi possível configurar TLS de saída HikCentral.",e);}
 }
 private static String required(String name){
  String s=System.getenv(name);
  if(s==null||s.isBlank())throw new IllegalArgumentException("Configuração obrigatória ausente: "+name);
  return s.trim();
 }
 /** Formato herdado do aplicativo; validar assinatura com guia 3.1.0 no ambiente real. */
 public static String canonical(String path,String key,String timestamp){
  return "POST\n*/*\napplication/json\nx-ca-key:"+key+"\nx-ca-timestamp:"+timestamp+"\n"+path;
 }
 public static String signature(String path,String key,String secret,String timestamp){
  try{
   Mac mac=Mac.getInstance("HmacSHA256");
   mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
   return Base64.getEncoder().encodeToString(mac.doFinal(canonical(path,key,timestamp).getBytes(StandardCharsets.UTF_8)));
  }catch(GeneralSecurityException e){throw new IllegalStateException(e);}
 }
 private JsonNode post(String path,JsonNode body) {
  String timestamp=Long.toString(System.currentTimeMillis());
  try {
   HttpRequest req=HttpRequest.newBuilder(base.resolve(path))
    .timeout(Duration.ofSeconds(18))
    .header("Content-Type","application/json")
    .header("Accept","*/*")
    .header("X-Ca-Key",key)
    .header("X-Ca-Timestamp",timestamp)
    .header("X-Ca-Signature-Headers","x-ca-key,x-ca-timestamp")
    .header("X-Ca-Signature",signature(path,key,secret,timestamp))
    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
    .build();
   HttpResponse<byte[]> resp=client.send(req,HttpResponse.BodyHandlers.ofByteArray());
   if(resp.statusCode()<200||resp.statusCode()>299)
    throw new RemoteFailure("Artemis HTTP "+resp.statusCode(),false);
   if(resp.body().length>2_000_000)throw new RemoteFailure("Resposta Artemis maior que o limite.",true);
   JsonNode root;
   try{root=mapper.readTree(resp.body());}
   catch(Exception e){throw new RemoteFailure("Resposta JSON inválida.",true);}
   if(root==null||!root.isObject()||!root.has("code")||
     !"0".equals(root.get("code").asText()))
    throw new RemoteFailure("Artemis retornou código não-zero ou estrutura inesperada.",false);
   return root.path("data");
  }catch(IOException e){throw new RemoteFailure("Falha de rede/TLS Artemis.",true);}
   catch(InterruptedException e){Thread.currentThread().interrupt();throw new RemoteFailure("Chamada Artemis interrompida.",true);}
 }
 @Override public String rootOrganization(){
  JsonNode root=post("/artemis/api/resource/v1/org/rootOrg",mapper.createObjectNode());
  String id=root.path("orgIndexCode").asText("");
  if(id.isBlank())throw new RemoteFailure("Organização raiz ausente.",false);
  return id;
 }
 @Override public List<Group> groups(){
  ArrayList<Group> out=new ArrayList<>();
  for(int p=1;p<=20;p++){
   ObjectNode req=mapper.createObjectNode().put("pageNo",p).put("pageSize",500).put("type",1);
   JsonNode data=post("/artemis/api/acs/v1/privilege/group",req);
   JsonNode list=data.path("list");
   if(!list.isArray())throw new RemoteFailure("Lista de níveis inválida.",false);
   for(JsonNode node:list){
    String id=node.path("privilegeGroupId").asText("");
    String name=node.path("privilegeGroupName").asText("");
    if(!id.isBlank()&&!name.isBlank())out.add(new Group(id,name));
   }
   if(list.size()<500||out.size()>=data.path("total").asInt(Integer.MAX_VALUE))return out;
  }
  throw new RemoteFailure("Paginação Artemis excedeu limite.",false);
 }
 @Override public String createPerson(String fullName,int gender,byte[] jpeg,String org,String code){
  String[] parts=fullName.trim().split("\\s+",2);
  OffsetDateTime now=OffsetDateTime.now().withNano(0);
  ObjectNode request=mapper.createObjectNode();
  request.put("personCode",code).put("personGivenName",parts[0])
   .put("personFamilyName",parts.length>1?parts[1]:"-").put("gender",gender)
   .put("orgIndexCode",org).put("beginTime",now.toString())
   .put("endTime",now.plusYears(10).toString());
  ArrayNode faces=mapper.createArrayNode();
  faces.add(mapper.createObjectNode().put("faceData",Base64.getEncoder().encodeToString(jpeg)));
  request.set("faces",faces);
  JsonNode data=post("/artemis/api/resource/v1/person/single/add",request);
  String id=data.isTextual()?data.asText():data.path("personId").asText("");
  if(id.isBlank())throw new RemoteFailure("Resposta sem personId; verificar manualmente antes de repetir.",true);
  return id;
 }
 @Override public void assign(String personId,String groupId){
  ObjectNode req=mapper.createObjectNode().put("privilegeGroupId",groupId).put("type",1);
  req.set("list",mapper.createArrayNode().add(mapper.createObjectNode().put("id",personId)));
  post("/artemis/api/acs/v1/privilege/group/single/addPersons",req);
 }
 @Override public void reapply(){
  post("/artemis/api/visitor/v1/auth/reapplication",mapper.createObjectNode());
 }
 @Override public boolean allApplied(){
  ObjectNode req=mapper.createObjectNode().put("applicationResultType",1)
    .put("pageNo",1).put("pageSize",10).put("type",1);
  JsonNode stats=post("/artemis/api/acs/v1/auth/applicationResult",req).path("statisticResult");
  for(String k:List.of("unApplicationCount","applicationFailCount","editAndUnApplicationCount"))
   if(!stats.has(k)||!stats.get(k).canConvertToInt()||stats.get(k).asInt()!=0)return false;
  return true;
 }
 public static class RemoteFailure extends RuntimeException {
  public final boolean ambiguous;
  public RemoteFailure(String message,boolean ambiguous){super(message);this.ambiguous=ambiguous;}
 }
}
