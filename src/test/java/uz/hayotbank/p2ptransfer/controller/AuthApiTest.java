package uz.hayotbank.p2ptransfer.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import uz.hayotbank.p2ptransfer.IntegrationTest;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.time.temporal.ChronoUnit.HOURS;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.WWW_AUTHENTICATE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthApiTest extends IntegrationTest {

    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    void loginGivesTokenThatOpensTheApi() throws Exception {
        // aziz from the dev data, all dev users have the password "password"
        MvcResult result = login("aziz", "password")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andReturn();
        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(get("/api/users/1").header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("aziz"));
    }

    @Test
    void newUserCanLogInRightAfterRegistration() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "sardor", "fullName": "Sardor Rakhimov", "password": "sardor-secret"}
                                """))
                .andExpect(status().isCreated());

        login("sardor", "sardor-secret")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void wrongPasswordIs401() throws Exception {
        login("aziz", "not-his-password")
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Wrong username or password"));
    }

    @Test
    void unknownUsernameGetsTheSameAnswer() throws Exception {
        // a different answer would tell which usernames exist
        login("nobody", "password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Wrong username or password"));
    }

    @Test
    void emptyLoginIs400() throws Exception {
        login("", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("username", "password")));
    }

    @Test
    void requestWithoutTokenIs401() throws Exception {
        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientId": 2, "amount": 300.00}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.instance").value("/api/transfers"));

        vendor.verify(0, postRequestedFor(urlEqualTo(VENDOR_PAYMENTS)));
    }

    @Test
    void tokenSignedWithAnotherSecretIs401() throws Exception {
        // somebody made a token "for aziz" without knowing our secret
        SecretKey otherSecret = new SecretKeySpec(
                "somebody-elses-secret-0123456789".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        String forged = NimbusJwtEncoder.withSecretKey(otherSecret).build()
                .encode(JwtEncoderParameters.from(claims(1L, Instant.now().plus(1, HOURS))))
                .getTokenValue();

        mockMvc.perform(get("/api/users/1").header(AUTHORIZATION, "Bearer " + forged))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void expiredTokenIs401() throws Exception {
        // signed with our secret, but it ran out an hour ago
        String expired = jwtEncoder.encode(JwtEncoderParameters.from(claims(1L, Instant.now().minus(1, HOURS))))
                .getTokenValue();

        mockMvc.perform(get("/api/users/1").header(AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail", containsString("expired")));
    }

    @Test
    void garbageInsteadOfTokenIs401() throws Exception {
        mockMvc.perform(get("/api/users/1").header(AUTHORIZATION, "Bearer not-a-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "%s", "password": "%s"}
                        """.formatted(username, password)));
    }

    private static JwtClaimsSet claims(Long userId, Instant expiresAt) {
        return JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .issuedAt(expiresAt.minus(1, HOURS))
                .expiresAt(expiresAt)
                .build();
    }
}
