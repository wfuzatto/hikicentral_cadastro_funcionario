package br.com.acquavale.bridge;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class DeviceAuthFilterTest {
 @Test void revokedTokenMustNotAuthenticate() throws Exception {
  JdbcTemplate db=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:auth"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa",""));
  db.execute("CREATE TABLE device_tokens(device_id VARCHAR(36),token_sha256 CHAR(64),revoked BOOLEAN)");
  UUID id=UUID.randomUUID();String token=TokenCrypto.generate(id);
  db.update("INSERT INTO device_tokens VALUES(?,?,FALSE)",id.toString(),TokenCrypto.hash(token));
  DeviceAuthFilter filter=new DeviceAuthFilter(db);
  MockHttpServletRequest request=new MockHttpServletRequest("GET","/api/v1/organization");
  request.addHeader("Authorization","Bearer "+token);
  MockHttpServletResponse response=new MockHttpServletResponse();
  filter.doFilter(request,response,(req,res)->{});
  assertEquals(200,response.getStatus());
  db.update("UPDATE device_tokens SET revoked=TRUE");
  MockHttpServletRequest request2=new MockHttpServletRequest("GET","/api/v1/organization");
  request2.addHeader("Authorization","Bearer "+token);
  MockHttpServletResponse response2=new MockHttpServletResponse();
  filter.doFilter(request2,response2,(req,res)->{});
  assertEquals(401,response2.getStatus());
 }
}
