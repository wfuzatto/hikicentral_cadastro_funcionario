package br.com.acquavale.bridge;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.io.*;
import java.util.*;

@RestController @Profile("!admin")
@RequestMapping("/api/v1")
public class EnrollmentController {
 private final EnrollmentService service;private final ArtemisGateway api;
 private final ObjectMapper json=new ObjectMapper();
 public EnrollmentController(EnrollmentService service,ArtemisGateway api){this.service=service;this.api=api;}
 @GetMapping("/health") public Map<String,String> health(){return Map.of("status","ok");}
 private static String device(HttpServletRequest req){return (String)req.getAttribute(DeviceAuthFilter.DEVICE);}
 private static UUID uuid(String value) {
  try{return UUID.fromString(value);}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"UUID inválido.");}
 }
 @GetMapping("/organization") public Map<String,String> org(){return Map.of("orgId",api.rootOrganization());}
 @GetMapping("/access-groups") public List<ArtemisGateway.Group> groups(){return api.groups().stream()
  .filter(g->Set.of("REFEITORIO","DESENVOLVIMENTO","LOJA ACQUAVALE",
   "CATRACAS SABIA","SAIDA ACQUAVALE","ENTRADA ACQUAVALE")
   .contains(EnrollmentService.normalized(g.name()))).toList();}
 @PostMapping(value="/enrollments",consumes=MediaType.APPLICATION_JSON_VALUE)
 public ResponseEntity<EnrollmentService.View> enroll(HttpServletRequest req,
  @RequestHeader("Idempotency-Key") String key) throws IOException {
  // Limite efetivo mesmo para Transfer-Encoding: chunked.
  byte[] payload=req.getInputStream().readNBytes(750_001);
  if(payload.length>750_000)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
  JsonNode body;
  try{body=json.readTree(payload);}
  catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"JSON inválido.");}
  if(body==null||!body.isObject())throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
  return response(service.enroll(device(req),uuid(key),body));
 }
 @GetMapping("/enrollments/{key}")
 public ResponseEntity<EnrollmentService.View> status(HttpServletRequest req,@PathVariable String key){
  return response(service.status(device(req),uuid(key)));
 }
 @PostMapping("/enrollments/{key}/resume")
 public ResponseEntity<EnrollmentService.View> resume(HttpServletRequest req,@PathVariable String key){
  return response(service.resume(device(req),uuid(key)));
 }
 private ResponseEntity<EnrollmentService.View> response(EnrollmentService.View view){
  if(view==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  HttpStatus code="COMPLETED".equals(view.status())?HttpStatus.OK:
   view.needsManualReconciliation()?HttpStatus.CONFLICT:HttpStatus.ACCEPTED;
  return ResponseEntity.status(code).body(view);
 }
 @ExceptionHandler(ArtemisClient.RemoteFailure.class)
 public ResponseEntity<Map<String,String>> remote(ArtemisClient.RemoteFailure ex) {
  return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error","HIKCENTRAL_UNAVAILABLE"));
 }
 @ExceptionHandler(IllegalArgumentException.class)
 public ResponseEntity<Map<String,String>> invalid(IllegalArgumentException ex) {
  return ResponseEntity.unprocessableEntity().body(Map.of("error","INVALID_REQUEST","message",ex.getMessage()));
 }
}
