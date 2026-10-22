package br.com.acquavale.bridge;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EnrollmentServiceTest {
 private JdbcTemplate jdbc;
 private ArtemisGateway api;
 private EnrollmentService service;
 private final ObjectMapper mapper=new ObjectMapper();
 private final String device=UUID.randomUUID().toString();
 @BeforeEach void prepare(){
  jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa",""));
  jdbc.execute("CREATE TABLE enrollments(device_id VARCHAR(36),idempotency_key VARCHAR(36),request_sha256 CHAR(64),"+
   "status VARCHAR(40),person_code VARCHAR(32),person_id VARCHAR(128),group_ids CLOB,next_group INTEGER,"+
   "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(device_id,idempotency_key))");
  api=mock(ArtemisGateway.class);
  when(api.rootOrganization()).thenReturn("root");
  when(api.groups()).thenReturn(List.of(new ArtemisGateway.Group("group-A","REFEITORIO")));
  when(api.createPerson(anyString(),anyInt(),any(byte[].class),anyString(),anyString())).thenReturn("person-123");
  when(api.allApplied()).thenReturn(true);
  service=new EnrollmentService(jdbc,api);
 }
 private ObjectNode request() throws Exception {
  BufferedImage image=new BufferedImage(340,340,BufferedImage.TYPE_INT_RGB);
  Random rnd=new Random(123);
  for(int y=0;y<340;y++)for(int x=0;x<340;x++)image.setRGB(x,y,rnd.nextInt(0x1000000));
  ByteArrayOutputStream stream=new ByteArrayOutputStream();
  ImageIO.write(image,"jpg",stream);
  ObjectNode n=mapper.createObjectNode();
  n.put("fullName","Pessoa Teste").put("gender",1);
  n.set("groups",mapper.createArrayNode().add("REFEITORIO"));
  n.put("photoJpegBase64",Base64.getEncoder().encodeToString(stream.toByteArray()));
  return n;
 }
 @Test void sameKeyNeverCallsCreateTwice() throws Exception {
  UUID key=UUID.randomUUID();ObjectNode body=request();
  assertEquals("COMPLETED",service.enroll(device,key,body).status());
  assertEquals("COMPLETED",service.enroll(device,key,body).status());
  verify(api,times(1)).createPerson(anyString(),anyInt(),any(byte[].class),anyString(),anyString());
  verify(api,times(1)).assign("person-123","group-A");
 }
 @Test void ambiguousCreateBlocksRetry() throws Exception {
  UUID key=UUID.randomUUID();
  when(api.createPerson(anyString(),anyInt(),any(byte[].class),anyString(),anyString()))
    .thenThrow(new ArtemisClient.RemoteFailure("timeout",true));
  assertEquals("AMBIGUOUS",service.enroll(device,key,request()).status());
  assertEquals("AMBIGUOUS",service.resume(device,key).status());
  verify(api,times(1)).createPerson(anyString(),anyInt(),any(byte[].class),anyString(),anyString());
 }
 @Test void rejectedGroupCanResumeWithoutCreatingPersonAgain() throws Exception {
  UUID key=UUID.randomUUID();
  doThrow(new ArtemisClient.RemoteFailure("rejected",false)).doNothing().when(api).assign("person-123","group-A");
  assertEquals("PERMISSIONS_PENDING",service.enroll(device,key,request()).status());
  assertEquals("COMPLETED",service.resume(device,key).status());
  verify(api,times(1)).createPerson(anyString(),anyInt(),any(byte[].class),anyString(),anyString());
 }
 @Test void uncertainSyncDoesNotReturnComplete() throws Exception {
  UUID key=UUID.randomUUID();
  when(api.allApplied()).thenReturn(false,true);
  assertEquals("SYNC_PENDING",service.enroll(device,key,request()).status());
  assertEquals("COMPLETED",service.resume(device,key).status());
 }
 @Test void deniedSectorsNeverMutateRemote() throws Exception {
  ObjectNode req=request();
  req.set("groups",mapper.createArrayNode().add("ADMINISTRADOR"));
  assertThrows(IllegalArgumentException.class,()->service.enroll(device,UUID.randomUUID(),req));
  verify(api,never()).createPerson(anyString(),anyInt(),any(byte[].class),anyString(),anyString());
 }
 @Test void conflictingPayloadUnderSameKeyRejected() throws Exception {
  UUID key=UUID.randomUUID();ObjectNode req=request();
  service.enroll(device,key,req);
  ObjectNode changed=req.deepCopy();changed.put("fullName","Outra Pessoa");
  assertThrows(IllegalArgumentException.class,()->service.enroll(device,key,changed));
 }
}
