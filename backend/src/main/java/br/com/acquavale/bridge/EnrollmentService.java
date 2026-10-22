package br.com.acquavale.bridge;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Service @Profile("!admin")
public class EnrollmentService {
 private final JdbcTemplate jdbc;private final ArtemisGateway api;
 private final ObjectMapper json=new ObjectMapper();
 private static final Set<String> ALLOWED=Set.of("REFEITORIO","DESENVOLVIMENTO",
  "LOJA ACQUAVALE","CATRACAS SABIA","SAIDA ACQUAVALE","ENTRADA ACQUAVALE");
 record Entry(String device,String key,String hash,String status,String code,String id,String ids,int next){}
 public record View(String idempotencyKey,String status,String personId,int groupsRemaining,
                    boolean needsManualReconciliation){}
 public EnrollmentService(JdbcTemplate jdbc,ArtemisGateway api){this.jdbc=jdbc;this.api=api;}

 private Entry get(String device,UUID key){
  List<Entry> entries=jdbc.query("SELECT device_id,idempotency_key,request_sha256,status,person_code,person_id,group_ids,next_group "+
   "FROM enrollments WHERE device_id=? AND idempotency_key=?",(rs,i)->new Entry(
    rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),
    rs.getString(5),rs.getString(6),rs.getString(7),rs.getInt(8)),device,key.toString());
  return entries.isEmpty()?null:entries.get(0);
 }
 private List<String> ids(Entry e){
  try {
   JsonNode n=json.readTree(e.ids);
   if(!n.isArray())throw new IllegalStateException("Grupos persistidos inválidos.");
   ArrayList<String> out=new ArrayList<>();
   for(JsonNode item:n)out.add(item.asText());
   return out;
  }catch(IOException ex){throw new IllegalStateException("Diário de matrícula inválido.");}
 }
 private View view(Entry e) {
  boolean manual=Set.of("AMBIGUOUS","AMBIGUOUS_ASSIGNMENT").contains(e.status)||
   Set.of("CREATING","ASSIGNING").contains(e.status);
  return new View(e.key,e.status,e.id,Math.max(0,ids(e).size()-e.next),manual);
 }
 public View status(String device,UUID key){
  Entry e=get(device,key);
  if(e==null)return null;
  if("CREATING".equals(e.status)||"ASSIGNING".equals(e.status)){
   transition(e,"AMBIGUOUS",e.id,e.next);
   e=get(device,key);
  }
  return view(e);
 }
 private void transition(Entry e,String status,String id,int next){
  jdbc.update("UPDATE enrollments SET status=?,person_id=?,next_group=?,updated_at=CURRENT_TIMESTAMP "+
    "WHERE device_id=? AND idempotency_key=?",status,id,next,e.device,e.key);
 }
 public static String normalized(String s) {
  return Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}+","")
   .trim().replaceAll("\\s+"," ").toUpperCase(Locale.ROOT);
 }
 private byte[] jpeg(JsonNode photo) {
  if(!photo.isTextual()||photo.textValue().length()>600_000)
   throw new IllegalArgumentException("Foto ausente ou excessiva.");
  byte[] bytes;
  try{bytes=Base64.getDecoder().decode(photo.asText());}
  catch(IllegalArgumentException e){throw new IllegalArgumentException("Foto Base64 inválida.");}
  if(bytes.length<10_000||bytes.length>350_000||(bytes[0]&255)!=255||(bytes[1]&255)!=216||
    (bytes[bytes.length-2]&255)!=255||(bytes[bytes.length-1]&255)!=217)
   throw new IllegalArgumentException("Foto deve ser JPEG válido entre 10 e 350 KB.");
  try(ImageInputStream in=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
   Iterator<ImageReader> readers=ImageIO.getImageReaders(in);
   if(!readers.hasNext())throw new IllegalArgumentException("JPEG não reconhecido.");
   ImageReader reader=readers.next();
   try {
    reader.setInput(in,true,true);
    int w=reader.getWidth(0),h=reader.getHeight(0);
    if(w<120||h<120||(long)w*h>10_000_000)
     throw new IllegalArgumentException("Resolução de foto fora do limite.");
   }finally{reader.dispose();}
  }catch(IOException ex){throw new IllegalArgumentException("JPEG corrompido.");}
  return bytes;
 }
 public synchronized View enroll(String device,UUID key,JsonNode body){
  String name=body.path("fullName").asText("").trim().replaceAll("\\s+"," ");
  int gender=body.path("gender").asInt(-1);
  JsonNode groups=body.path("groups");
  if(name.length()<2||name.length()>120||!name.matches("[\\p{L} .'-]+"))
   throw new IllegalArgumentException("Nome inválido.");
  if(gender<0||gender>2)throw new IllegalArgumentException("Sexo inválido.");
  if(!groups.isArray()||groups.isEmpty()||groups.size()>6)
   throw new IllegalArgumentException("Selecione 1 a 6 setores.");
  LinkedHashSet<String> selected=new LinkedHashSet<>();
  for(JsonNode group:groups) {
   if(!group.isTextual())throw new IllegalArgumentException("Setor inválido.");
   String normalized=normalized(group.asText());
   if(!ALLOWED.contains(normalized)||!selected.add(normalized))
    throw new IllegalArgumentException("Setor desconhecido ou repetido.");
  }
  byte[] image=jpeg(body.path("photoJpegBase64"));
  String digest=TokenCrypto.hash((name+"|"+gender+"|"+
   selected.stream().sorted().collect(Collectors.joining("|"))+"|"+TokenCrypto.hash(image))
    .getBytes(StandardCharsets.UTF_8));
  Entry existing=get(device,key);
  if(existing!=null) {
   if(!existing.hash.equals(digest))throw new IllegalArgumentException(
    "Chave de idempotência já usada para outro cadastro.");
   return status(device,key);
  }
  // Consultas somente de leitura antes de registrar tentativa mutável.
  String org=api.rootOrganization();
  List<ArtemisGateway.Group> groupsAvailable=api.groups();
  List<String> groupIds=new ArrayList<>();
  for(String selectedName:selected) {
   List<ArtemisGateway.Group> matching=groupsAvailable.stream()
    .filter(g->normalized(g.name()).equals(selectedName)).toList();
   if(matching.size()!=1)throw new IllegalArgumentException("Setor não identificado de forma única: "+selectedName);
   groupIds.add(matching.get(0).id());
  }
  String code="ACQ"+TokenCrypto.hash(device+"|"+key).substring(0,26).toUpperCase(Locale.ROOT);
  try{
   jdbc.update("INSERT INTO enrollments(device_id,idempotency_key,request_sha256,status,person_code,group_ids,next_group) "+
    "VALUES(?,?,?,'RECEIVED',?,?,0)",device,key.toString(),digest,code,json.writeValueAsString(groupIds));
  }catch(Exception e){throw new IllegalStateException("Não foi possível persistir tentativa.");}
  Entry entry=get(device,key);
  transition(entry,"CREATING",null,0); // Durável ANTES da chamada de criação.
  String personId;
  try {
   personId=api.createPerson(name,gender,image,org,code);
  }catch(RuntimeException e){
   transition(entry,"AMBIGUOUS",null,0); // Falha ou timeout: nunca recriar automaticamente.
   return view(get(device,key));
  }
  transition(entry,"PERMISSIONS_PENDING",personId,0);
  return advance(get(device,key));
 }
 public synchronized View resume(String device,UUID key) {
  Entry e=get(device,key);
  if(e==null)return null;
  if(Set.of("PERMISSIONS_PENDING","SYNC_PENDING").contains(e.status))
   return advance(e);
  return status(device,key);
 }
 private View advance(Entry entry){
  Entry e=entry;
  List<String> groupIds=ids(e);
  if("PERMISSIONS_PENDING".equals(e.status)){
   for(int i=e.next;i<groupIds.size();i++){
    transition(e,"ASSIGNING",e.id,i); // Falha/crash durante essa chamada exige reconciliação.
    try{api.assign(e.id,groupIds.get(i));}
    catch(ArtemisClient.RemoteFailure ex){
     transition(e,ex.ambiguous?"AMBIGUOUS_ASSIGNMENT":"PERMISSIONS_PENDING",e.id,i);
     return view(get(e.device,UUID.fromString(e.key)));
    }catch(RuntimeException ex){
     transition(e,"AMBIGUOUS_ASSIGNMENT",e.id,i);
     return view(get(e.device,UUID.fromString(e.key)));
    }
    transition(e,"PERMISSIONS_PENDING",e.id,i+1);
   }
   transition(e,"SYNC_PENDING",e.id,groupIds.size());
  }
  e=get(e.device,UUID.fromString(e.key));
  if("SYNC_PENDING".equals(e.status)) {
   try {
    api.reapply(); // Reaplicação não cria Persons.
    // Status global 0/0/0 é o único resultado reconhecido como confirmado.
    if(api.allApplied())transition(e,"COMPLETED",e.id,groupIds.size());
   }catch(RuntimeException ignored) {
    // Não registrar corpo, imagem, headers ou segredo. Pendente para consulta posterior.
   }
  }
  return view(get(e.device,UUID.fromString(e.key)));
 }
}
