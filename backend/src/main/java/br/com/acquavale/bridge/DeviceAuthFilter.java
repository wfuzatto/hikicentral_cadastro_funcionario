package br.com.acquavale.bridge;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.*;
@Component @Profile("!admin")
public class DeviceAuthFilter extends OncePerRequestFilter {
 public static final String DEVICE="authenticatedDevice";
 private final JdbcTemplate jdbc;
 public DeviceAuthFilter(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)
 throws ServletException,IOException {
  if("GET".equals(req.getMethod()) && "/api/v1/health".equals(req.getRequestURI())) {
   chain.doFilter(req,res);return;
  }
  if(!req.getRequestURI().startsWith("/api/v1/")) {res.sendError(404);return;}
  String header=req.getHeader("Authorization");
  if(header==null||!header.startsWith("Bearer ")){res.sendError(401);return;}
  String token=header.substring(7);
  String[] parts=token.split("\\.",2);
  UUID id;
  try{id=UUID.fromString(parts[0]);}catch(Exception e){res.sendError(401);return;}
  if(parts.length!=2||parts[1].length()<42||parts[1].length()>50){res.sendError(401);return;}
  List<String> hashes=jdbc.query("SELECT token_sha256 FROM device_tokens WHERE device_id=? AND revoked=FALSE",
   (rs,n)->rs.getString(1),id.toString());
  if(hashes.size()!=1||!TokenCrypto.matches(token,hashes.get(0))){res.sendError(401);return;}
  req.setAttribute(DEVICE,id.toString());
  res.setHeader("Cache-Control","no-store");
  chain.doFilter(req,res);
 }
}
