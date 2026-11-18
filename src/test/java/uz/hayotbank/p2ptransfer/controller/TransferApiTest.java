package uz.hayotbank.p2ptransfer.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import uz.hayotbank.p2ptransfer.IntegrationTest;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TransferApiTest extends IntegrationTest {

    @Test
    void approvedTransferMovesMoney() throws Exception {
        Long sender = createUser("1000.00");
        Long recipient = createUser("0.00");
        vendorAnswers("APPROVED");

        sendTransfer(sender, recipient, "300.00")
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

        sendTransfer(sender, recipient, "300.00")
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

        sendTransfer(sender, recipient, "300.00")
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

        MvcResult result = sendTransfer(sender, recipient, "300.00")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        // the money has left the sender but has not reached the recipient: it is reserved
        assertThat(balanceOf(sender)).isEqualByComparingTo("700.00");
        assertThat(balanceOf(recipient)).isEqualByComparingTo("0.00");
        vendor.verify(3, postRequestedFor(urlEqualTo(VENDOR_PAYMENTS)));

        Number transferId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        mockMvc.perform(get("/api/transfers/" + transferId).header(AUTHORIZATION, bearer(sender)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void retryAfterTemporaryErrorUsesSameReference() throws Exception {
        Long sender = createUser("1000.00");
        Long recipient = createUser("0.00");
        vendorFailsOnceThenAnswers("APPROVED");

        MvcResult result = sendTransfer(sender, recipient, "300.00")
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

        sendTransfer(sender, recipient, "300.00")
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("User " + sender + " does not have enough money for this transfer"));

        assertThat(balanceOf(sender)).isEqualByComparingTo("100.00");
        vendor.verify(0, postRequestedFor(urlEqualTo(VENDOR_PAYMENTS)));
    }

    @Test
    void moneyAlwaysGoesFromTheTokenOwner() throws Exception {
        Long sender = createUser("1000.00");
        Long recipient = createUser("0.00");
        vendorAnswers("APPROVED");
        BigDecimal azizBefore = balanceOf(1L);

        // an old client still sends senderId (here: aziz), it is ignored
        mockMvc.perform(post("/api/transfers")
                        .header(AUTHORIZATION, bearer(sender))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senderId": 1, "recipientId": %d, "amount": 300.00}
                                """.formatted(recipient)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderId").value(sender));

        assertThat(balanceOf(sender)).isEqualByComparingTo("700.00");
        assertThat(balanceOf(1L)).isEqualByComparingTo(azizBefore);
    }

    @Test
    void transferToYourselfIs400() throws Exception {
        Long user = createUser("1000.00");

        sendTransfer(user, user, "300.00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Sender and recipient must be different users"));
    }

    @Test
    void unknownRecipientIs404() throws Exception {
        Long sender = createUser("1000.00");

        sendTransfer(sender, 999999L, "300.00")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("User 999999 not found"));
    }

    @Test
    void invalidFieldsAre400() throws Exception {
        mockMvc.perform(post("/api/transfers")
                        .header(AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 10.555}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("recipientId", "amount")));
    }

    @Test
    void senderSeesTransfer() throws Exception {
        // transfer 1 comes from the dev test data: aziz (1) -> ivan (2), the reference from the assignment
        mockMvc.perform(get("/api/transfers/1").header(AUTHORIZATION, bearer(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value("7f3c9a2e-5b1d-4e8a-9c6f-2d4b8e1a0f35"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(150000.0));
    }

    @Test
    void recipientSeesTransferToo() throws Exception {
        mockMvc.perform(get("/api/transfers/1").header(AUTHORIZATION, bearer(2L)))
                .andExpect(status().isOk());
    }

    @Test
    void otherUsersCannotSeeTransfer() throws Exception {
        // dilnoza (3) has nothing to do with transfer 1
        mockMvc.perform(get("/api/transfers/1").header(AUTHORIZATION, bearer(3L)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Only the sender and the recipient can see transfer 1"));
    }

    @Test
    void unknownTransferIs404() throws Exception {
        mockMvc.perform(get("/api/transfers/999999").header(AUTHORIZATION, bearer(1L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Transfer 999999 not found"));
    }

    private ResultActions sendTransfer(Long sender, Long recipient, String amount) throws Exception {
        return mockMvc.perform(post("/api/transfers")
                .header(AUTHORIZATION, bearer(sender))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"recipientId": %d, "amount": %s}
                        """.formatted(recipient, amount)));
    }
}
