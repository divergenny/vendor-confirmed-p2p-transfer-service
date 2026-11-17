package uz.hayotbank.p2ptransfer.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import uz.hayotbank.p2ptransfer.IntegrationTest;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserApiTest extends IntegrationTest {

    @Test
    void createsUserWithZeroBalance() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "bekzod", "fullName": "Bekzod Nazarov"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/users/")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value("bekzod"))
                .andExpect(jsonPath("$.fullName").value("Bekzod Nazarov"))
                .andExpect(jsonPath("$.balance").value(0.0));
    }

    @Test
    void returnsUserWithBalance() throws Exception {
        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("aziz"))
                .andExpect(jsonPath("$.fullName").value("Aziz Karimov"))
                .andExpect(jsonPath("$.balance").value(1000000.0));
    }

    @Test
    void unknownUserIs404() throws Exception {
        mockMvc.perform(get("/api/users/999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("User 999999 not found"))
                .andExpect(jsonPath("$.instance").value("/api/users/999999"));
    }

    @Test
    void takenUsernameIs409() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "aziz", "fullName": "Another Aziz"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Username 'aziz' is already taken"));
    }

    @Test
    void invalidFieldsAre400WithDetails() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "Not Valid", "fullName": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("username", "fullName")));
    }

    @Test
    void fullNameCanBeInAnyLanguage() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "ivan.petrov", "fullName": "Иван Петров"}
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
                                {"username": "аziz", "fullName": "Fake Aziz"}
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
                                {"username": "ruslan", "fullName": ""}
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
        mockMvc.perform(get("/api/users/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
