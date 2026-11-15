package logistics.identity.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jwt.*;
import contracts.v1.*;
import contracts.v1.Role;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.context.annotation.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
class SecurityConfig {
  @Bean
  org.springframework.beans.factory.InitializingBean profileGuard(
      org.springframework.core.env.Environment env) {
    return () -> {
      var active = java.util.Set.of(env.getActiveProfiles());
      if ((active.contains("dev") || active.contains("test"))
          && (active.contains("prod") || active.contains("integration")))
        throw new IllegalStateException("dev/test fake adapters cannot run with prod/integration");
    };
  }

  @Bean
  BCryptPasswordEncoder passwords() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  SecurityFilterChain chain(HttpSecurity http, JwtService jwt, ObjectMapper mapper)
      throws Exception {
    http.csrf(c -> c.disable())
        .sessionManagement(
            s ->
                s.sessionCreationPolicy(
                    org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a -> a.requestMatchers("/api/v1/auth/login").permitAll().anyRequest().authenticated())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                    (req, res, ex) -> {
                      res.setStatus(401);
                      res.setContentType("application/problem+json");
                      mapper.writeValue(res.getOutputStream(), Errors.body("UNAUTHENTICATED"));
                    }))
        .addFilterBefore(
            new OncePerRequestFilter() {
              protected void doFilterInternal(
                  HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                  throws IOException, ServletException {
                String token = req.getHeader("Authorization");
                if (token != null && token.startsWith("Bearer ")) {
                  try {
                    ActorContext actor = jwt.verify(token.substring(7));
                    SecurityContextHolder.getContext()
                        .setAuthentication(
                            new UsernamePasswordAuthenticationToken(actor, null, List.of()));
                  } catch (Exception ignored) {
                    SecurityContextHolder.clearContext();
                  }
                }
                chain.doFilter(req, res);
              }
            },
            UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  CurrentActorPort currentActor() {
    return () -> {
      var auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth == null || !(auth.getPrincipal() instanceof ActorContext actor))
        throw new ContractException("UNAUTHENTICATED");
      return actor;
    };
  }

  @Bean
  @Profile("dev")
  ApplicationRunner seed(
      JdbcTemplate db,
      BCryptPasswordEncoder encoder,
      @Value("${app.demo-password}") String password) {
    return args -> {
      if (password.isBlank()) throw new IllegalStateException("DEMO_PASSWORD must not be blank");
      String hash = encoder.encode(password);
      String[] names = {"admin", "operator", "store", "driver", "other-store"};
      String[] roles = {"ADMIN", "OPERATOR", "STORE", "DRIVER", "STORE"};
      for (int i = 0; i < 5; i++) {
        UUID id = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa" + (i + 1));
        UUID store = i == 2 ? Fixtures.STORE : i == 4 ? Fixtures.OTHER_STORE : null;
        UUID driver = i == 3 ? Fixtures.DRIVER : null;
        int count =
            db.update(
                "UPDATE identity.users SET password_hash=?, role=?, store_id=?, driver_id=? WHERE"
                    + " id=?",
                hash,
                roles[i],
                store,
                driver,
                id);
        if (count == 0)
          db.update(
              "INSERT INTO identity.users(id,email,password_hash,role,store_id,driver_id) VALUES"
                  + " (?,?,?,?,?,?)",
              id,
              names[i] + "@example.invalid",
              hash,
              roles[i],
              store,
              driver);
      }
    };
  }
}

@Component
class JwtService {
  private final byte[] key;

  JwtService(@Value("${app.jwt-secret}") String secret) {
    key = secret.getBytes(StandardCharsets.UTF_8);
    if (key.length < 32)
      throw new IllegalStateException("JWT_SECRET must contain at least 32 bytes");
  }

  String sign(ActorContext a, Instant expires) {
    try {
      JWTClaimsSet.Builder b =
          new JWTClaimsSet.Builder()
              .subject(a.actorId().toString())
              .claim("role", a.role().name())
              .issueTime(new Date())
              .expirationTime(Date.from(expires));
      if (a.storeId() != null) b.claim("storeId", a.storeId().toString());
      if (a.driverId() != null) b.claim("driverId", a.driverId().toString());
      SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), b.build());
      jwt.sign(new MACSigner(key));
      return jwt.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
  }

  ActorContext verify(String token) throws Exception {
    SignedJWT jwt = SignedJWT.parse(token);
    if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())
        || !jwt.verify(new MACVerifier(key))) throw new Exception();
    JWTClaimsSet c = jwt.getJWTClaimsSet();
    if (c.getExpirationTime() == null || !c.getExpirationTime().after(new Date()))
      throw new Exception();
    Role role = Role.valueOf(c.getStringClaim("role"));
    String s = c.getStringClaim("storeId"), d = c.getStringClaim("driverId");
    if ((role == Role.STORE) != (s != null) || (role == Role.DRIVER) != (d != null))
      throw new Exception();
    return new ActorContext(
        UUID.fromString(c.getSubject()),
        role,
        s == null ? null : UUID.fromString(s),
        d == null ? null : UUID.fromString(d));
  }
}

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {
  record Login(String email, String password) {}

  record Response(String accessToken, String tokenType, Instant expiresAt, ActorContext actor) {}

  private final JdbcTemplate db;
  private final BCryptPasswordEncoder passwords;
  private final JwtService jwt;
  private final CurrentActorPort current;

  AuthController(
      JdbcTemplate db, BCryptPasswordEncoder passwords, JwtService jwt, CurrentActorPort current) {
    this.db = db;
    this.passwords = passwords;
    this.jwt = jwt;
    this.current = current;
  }

  @PostMapping("/login")
  Response login(@RequestBody Login input) {
    if (input.email() == null
        || input.email().isBlank()
        || input.email().length() > 254
        || input.password() == null
        || input.password().isBlank()) throw new ContractException("VALIDATION_ERROR");
    var rows =
        db.queryForList(
            "SELECT * FROM identity.users WHERE email=?", input.email().toLowerCase(Locale.ROOT));
    if (rows.isEmpty()
        || !passwords.matches(input.password(), (String) rows.get(0).get("password_hash")))
      throw new ContractException("UNAUTHENTICATED");
    var r = rows.get(0);
    ActorContext a =
        new ActorContext(
            (UUID) r.get("id"),
            Role.valueOf((String) r.get("role")),
            (UUID) r.get("store_id"),
            (UUID) r.get("driver_id"));
    Instant exp = Instant.now().plusSeconds(3600);
    return new Response(jwt.sign(a, exp), "Bearer", exp, a);
  }

  @GetMapping("/me")
  ActorContext me() {
    return current.requireActor();
  }
}
