package uz.hayotbank.p2ptransfer.controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import uz.hayotbank.p2ptransfer.IntegrationTest;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HistoryApiTest extends IntegrationTest {

    @Test
    void showsMoneyInAndOutNewestFirst() throws Exception {
        // ivan (id 2) from the dev data: got 150000 from aziz on Nov 10, sent 50000 to dilnoza on Nov 12
        mockMvc.perform(historyOf(2L).param("from", "2026-11-01").param("to", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].transferId").value(2))
                .andExpect(jsonPath("$.content[0].direction").value("OUTGOING"))
                .andExpect(jsonPath("$.content[0].counterpartyId").value(3))
                .andExpect(jsonPath("$.content[0].amount").value(50000.0))
                .andExpect(jsonPath("$.content[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.content[1].transferId").value(1))
                .andExpect(jsonPath("$.content[1].direction").value("INCOMING"))
                .andExpect(jsonPath("$.content[1].counterpartyId").value(1));
    }

    @Test
    void filtersByPeriod() throws Exception {
        mockMvc.perform(historyOf(2L).param("from", "2026-11-11").param("to", "2026-11-13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].transferId").value(2));
    }

    @Test
    void lastDayOfPeriodIsIncluded() throws Exception {
        // aziz (id 1): the declined transfer to dilnoza was made on Nov 14
        mockMvc.perform(historyOf(1L).param("from", "2026-11-14").param("to", "2026-11-14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("DECLINED"));
    }

    @Test
    void dayEndsAtMidnightInTashkent() throws Exception {
        Long sender = createUser("0.00");
        Long recipient = createUser("0.00");
        // 20:00 UTC on Nov 14 is already 01:00 on Nov 15 in Tashkent
        insertTransfer(sender, recipient, "2026-11-14T20:00:00Z");

        mockMvc.perform(historyOf(sender).param("from", "2026-11-14").param("to", "2026-11-14"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(historyOf(sender).param("from", "2026-11-15").param("to", "2026-11-15"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void splitsHistoryIntoPages() throws Exception {
        Long user = createUser("0.00");
        Long other = createUser("0.00");
        for (int day = 1; day <= 5; day++) {
            insertTransfer(user, other, "2026-10-0" + day + "T10:00:00Z");
        }

        mockMvc.perform(historyOf(user)
                        .param("from", "2026-10-01").param("to", "2026-10-31")
                        .param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].createdAt").value("2026-10-05T10:00:00Z"))
                .andExpect(jsonPath("$.content[1].createdAt").value("2026-10-04T10:00:00Z"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));

        mockMvc.perform(historyOf(user)
                        .param("from", "2026-10-01").param("to", "2026-10-31")
                        .param("page", "2").param("size", "2"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].createdAt").value("2026-10-01T10:00:00Z"));
    }

    @Test
    void fromAfterToIs400() throws Exception {
        mockMvc.perform(historyOf(1L).param("from", "2026-11-30").param("to", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("'from' (2026-11-30) must not be after 'to' (2026-11-01)"));
    }

    @Test
    void missingDateIs400() throws Exception {
        mockMvc.perform(historyOf(1L).param("from", "2026-11-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void wrongDateFormatIs400() throws Exception {
        mockMvc.perform(historyOf(1L).param("from", "01.11.2026").param("to", "2026-11-30"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tooBigPageIs400() throws Exception {
        mockMvc.perform(historyOf(1L)
                        .param("from", "2026-11-01").param("to", "2026-11-30").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors[0].field").value("size"))
                .andExpect(jsonPath("$.errors[0].message").value("must be less than or equal to 100"));
    }

    @Test
    void otherUsersHistoryIs403() throws Exception {
        // ivan (id 2) wants to see the history of aziz (id 1)
        mockMvc.perform(get("/api/users/1/history").header(AUTHORIZATION, bearer(2L))
                        .param("from", "2026-11-01").param("to", "2026-11-30"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("You can only see your own account"));
    }

    // a user asks for their own history
    private MockHttpServletRequestBuilder historyOf(Long userId) {
        return get("/api/users/" + userId + "/history").header(AUTHORIZATION, bearer(userId));
    }

    private void insertTransfer(Long sender, Long recipient, String createdAt) {
        OffsetDateTime time = OffsetDateTime.parse(createdAt);
        jdbcTemplate.update("""
                INSERT INTO transfers (reference, sender_id, recipient_id, amount, status, created_at, updated_at)
                VALUES (?, ?, ?, 100.00, 'COMPLETED', ?, ?)
                """, UUID.randomUUID(), sender, recipient, time, time);
    }
}
