package uz.hayotbank.p2ptransfer.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import uz.hayotbank.p2ptransfer.IntegrationTest;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TransferApiTest extends IntegrationTest {

    @Test
    void approvedTransferMovesMoney() throws Exception {
        Long sender = createUser("1000.00");
        Long recipient = createUser("0.00");
        vendorAnswers("APPROVED");

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(sender, recipient, "300.00")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/transfers/")))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.senderId").value(sender))
                .andExpect(jsonPath("$.recipientId").value(recipient))
                .andExpect(jsonPath("$.amount").value(300.0))
                .andExpect(jsonPath("$.reference").isNotEmpty());

        assertThat(balanceOf(sender)).isEqualByComparingTo("700.00");
        assertThat(balanceOf(recipient)).isEqualByComparingTo("300.00");
        vendor.verify(postRequestedFor(urlEqualTo(VENDOR_PAYMENTS))
                .withRequestBody(matchingJsonPath("$.payer", equalTo(sender.toString())))
                .withRequestBody(matchingJsonPath("$.payee", equalTo(recipient.toString())))
                .withRequestBody(matchingJsonPath("$[?(@.amount == 300.00)]")));
    }

    @Test
    void declinedTransferGivesMoneyBack() throws Exception {
        Long sender = createUser("1000.00");
        Long recipient = createUser("0.00");
        vendorAnswers("DECLINED");

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(sender, recipient, "300.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DECLINED"));

        assertThat(balanceOf(sender)).isEqualByComparingTo("1000.00");
        assertThat(balanceOf(recipient)).isEqualByComparingTo("0.00");
    }

    @Test
    void rejectedRequestFailsTransferAndGivesMoneyBack() throws Exception {
        Long sender = createUser("1000.00");
        Long recipient = createUser("0.00");
        vendorFailsWith(400);

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(sender, recipient, "300.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"));

        assertThat(balanceOf(sender)).isEqualByComparingTo("1000.00");
        vendor.verify(1, postRequestedFor(urlEqualTo(VENDOR_PAYMENTS)));
    }

    @Test
    void noAnswerFromVendorKeepsMoneyReserved() throws Exception {
        Long sender = createUser("1000.00");
        Long recipient = createUser("0.00");
        vendorFailsWith(503);

        MvcResult result = mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(sender, recipient, "300.00")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        // the money has left the sender but has not reached the recipient: it is reserved
        assertThat(balanceOf(sender)).isEqualByComparingTo("700.00");
        assertThat(balanceOf(recipient)).isEqualByComparingTo("0.00");
        vendor.verify(3, postRequestedFor(urlEqualTo(VENDOR_PAYMENTS)));

        Number transferId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        mockMvc.perform(get("/api/transfers/" + transferId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void retryAfterTemporaryErrorUsesSameReference() throws Exception {
        Long sender = createUser("1000.00");
        Long recipient = createUser("0.00");
        vendorFailsOnceThenAnswers("APPROVED");

        MvcResult result = mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(sender, recipient, "300.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andReturn();

        String reference = JsonPath.read(result.getResponse().getContentAsString(), "$.reference");
        vendor.verify(2, postRequestedFor(urlEqualTo(VENDOR_PAYMENTS))
                .withRequestBody(matchingJsonPath("$.reference", equalTo(reference))));
        assertThat(balanceOf(recipient)).isEqualByComparingTo("300.00");
    }

    @Test
    void notEnoughMoneyIs422AndVendorIsNotAsked() throws Exception {
        Long sender = createUser("100.00");
        Long recipient = createUser("0.00");

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(sender, recipient, "300.00")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("User " + sender + " does not have enough money for this transfer"));

        assertThat(balanceOf(sender)).isEqualByComparingTo("100.00");
        vendor.verify(0, postRequestedFor(urlEqualTo(VENDOR_PAYMENTS)));
    }

    @Test
    void transferToYourselfIs400() throws Exception {
        Long user = createUser("1000.00");

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(user, user, "300.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Sender and recipient must be different users"));
    }

    @Test
    void unknownRecipientIs404() throws Exception {
        Long sender = createUser("1000.00");

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(sender, 999999L, "300.00")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("User 999999 not found"));
    }

    @Test
    void invalidFieldsAre400() throws Exception {
        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientId": 2, "amount": 10.555}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("senderId", "amount")));
    }

    @Test
    void returnsTransfer() throws Exception {
        // transfer 1 comes from the dev test data: aziz -> ivan, the reference from the assignment
        mockMvc.perform(get("/api/transfers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value("7f3c9a2e-5b1d-4e8a-9c6f-2d4b8e1a0f35"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(150000.0));
    }

    @Test
    void unknownTransferIs404() throws Exception {
        mockMvc.perform(get("/api/transfers/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Transfer 999999 not found"));
    }

    private String transferJson(Long senderId, Long recipientId, String amount) {
        return """
                {"senderId": %d, "recipientId": %d, "amount": %s}
                """.formatted(senderId, recipientId, amount);
    }
}
