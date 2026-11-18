package uz.hayotbank.p2ptransfer.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import uz.hayotbank.p2ptransfer.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserApiTest extends IntegrationTest {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void createsUserWithZeroBalance() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "bekzod", "fullName": "Bekzod Nazarov", "password": "bekzod-secret"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/users/")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value("bekzod"))
                .andExpect(jsonPath("$.fullName").value("Bekzod Nazarov"))
                .andExpect(jsonPath("$.balance").value(0.0))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void storesOnlyPasswordHash() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "malika", "fullName": "Malika Yusupova", "password": "malika-secret"}
                                """))
                .andExpect(status().isCreated());

        String hash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE username = 'malika'", String.class);
        assertThat(hash).startsWith("$2a$");
        assertThat(passwordEncoder.matches("malika-secret", hash)).isTrue();
    }

    @Test
    void returnsUserWithBalance() throws Exception {
        mockMvc.perform(get("/api/users/1").header(AUTHORIZATION, bearer(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("aziz"))
                .andExpect(jsonPath("$.fullName").value("Aziz Karimov"))
                .andExpect(jsonPath("$.balance").value(1000000.0));
    }

    @Test
    void otherUsersAccountIs403() throws Exception {
        // ivan (id 2) wants to see the balance of aziz (id 1)
        mockMvc.perform(get("/api/users/1").header(AUTHORIZATION, bearer(2L)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("You can only see your own account"))
                .andExpect(jsonPath("$.instance").value("/api/users/1"));
    }

    @Test
    void takenUsernameIs409() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "aziz", "fullName": "Another Aziz", "password": "another-secret"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Username 'aziz' is already taken"));
    }

    @Test
    void invalidFieldsAre400WithDetails() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "Not Valid", "fullName": "", "password": "short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("username", "fullName", "password")));
    }

    @Test
    void fullNameCanBeInAnyLanguage() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "ivan.petrov", "fullName": "Иван Петров", "password": "ivan-secret"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullName").value("Иван Петров"));
    }

    @Test
    void usernameMustBeLatin() throws Exception {
        // the first letter is Cyrillic "а" (U+0430): on screen this login looks exactly like "aziz"
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "аziz", "fullName": "Fake Aziz", "password": "fake-secret"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("username"));
    }

    @Test
    void validationMessagesAreAlwaysInEnglish() throws Exception {
        mockMvc.perform(post("/api/users")
                        .header("Accept-Language", "ru")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "ruslan", "fullName": "", "password": "ruslan-secret"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("must not be blank"));
    }

    @Test
    void brokenJsonIs400() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not a json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void notNumericIdIs400() throws Exception {
        mockMvc.perform(get("/api/users/abc").header(AUTHORIZATION, bearer(1L)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
