package br.com.acquavale.bridge;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.UUID;
@Component @Profile("admin")
public class AdminCommands implements ApplicationRunner {
 private final JdbcTemplate jdbc;
 public AdminCommands(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @Override public void run(ApplicationArguments args) {
  String action=get(args,"admin.action");
  switch(action) {
   case "create" -> {
    String label=get(args,"admin.label");
    if(label.length()<2||label.length()>100)throw new IllegalArgumentException("Label 2-100 caracteres.");
    UUID id=UUID.randomUUID();String token=TokenCrypto.generate(id);
    jdbc.update("INSERT INTO device_tokens(device_id,label,token_sha256,revoked) VALUES(?,?,?,FALSE)",
     id.toString(),label,TokenCrypto.hash(token));
    System.out.println("Device ID: "+id);
    System.out.println("TOKEN - ÚNICA EXIBIÇÃO (guarde em local protegido): "+token);
   }
   case "rotate","revoke" -> {
    UUID id=UUID.fromString(get(args,"admin.device"));
    if(action.equals("revoke")) {
     System.out.println("Revogados: "+jdbc.update("UPDATE device_tokens SET revoked=TRUE,updated_at=CURRENT_TIMESTAMP WHERE device_id=?",id.toString()));
    } else {
     String token=TokenCrypto.generate(id);
     int rows=jdbc.update("UPDATE device_tokens SET token_sha256=?,revoked=FALSE,updated_at=CURRENT_TIMESTAMP WHERE device_id=?",
      TokenCrypto.hash(token),id.toString());
     if(rows!=1)throw new IllegalArgumentException("Dispositivo inexistente.");
     System.out.println("NOVO TOKEN - ÚNICA EXIBIÇÃO: "+token);
    }
   }
   case "reconcile-person" -> {
    UUID device=UUID.fromString(get(args,"admin.device"));
    UUID key=UUID.fromString(get(args,"admin.key"));
    String id=get(args,"admin.person-id");
    if(!"YES".equals(get(args,"admin.confirm-verified"))||!id.matches("[A-Za-z0-9_-]{1,128}"))
     throw new IllegalArgumentException("Confirme verificação presencial/administrativa no HikCentral.");
    int n=jdbc.update("UPDATE enrollments SET person_id=?,status='PERMISSIONS_PENDING',updated_at=CURRENT_TIMESTAMP "+
     "WHERE device_id=? AND idempotency_key=? AND status='AMBIGUOUS' AND person_id IS NULL",
     id,device.toString(),key.toString());
    System.out.println("Registros reconciliados: "+n);
   }
   default -> throw new IllegalArgumentException("admin.action deve ser create|rotate|revoke|reconcile-person");
  }
 }
 private static String get(ApplicationArguments a,String key){
  if(!a.containsOption(key)||a.getOptionValues(key).size()!=1)throw new IllegalArgumentException("Parâmetro obrigatório --"+key);
  return a.getOptionValues(key).get(0);
 }
}
