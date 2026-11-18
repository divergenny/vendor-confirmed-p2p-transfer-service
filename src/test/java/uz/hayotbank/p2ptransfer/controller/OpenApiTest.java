package uz.hayotbank.p2ptransfer.controller;

import org.junit.jupiter.api.Test;
import uz.hayotbank.p2ptransfer.IntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpenApiTest extends IntegrationTest {

    private static final String TRANSFER_RESPONSES = "$.paths['/api/transfers'].post.responses";

    @Test
    void specDescribesOurApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("P2P transfers"))
                .andExpect(jsonPath("$.paths['/api/users/{id}/history'].get").exists())
                .andExpect(jsonPath(TRANSFER_RESPONSES + "['202'].content['application/json'].schema.$ref")
                        .value("#/components/schemas/TransferResponse"))
                .andExpect(jsonPath(TRANSFER_RESPONSES + "['422'].content['application/problem+json'].schema.$ref")
                        .value("#/components/schemas/ProblemDetail"));
    }

    @Test
    void swaggerUiIsServed() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
