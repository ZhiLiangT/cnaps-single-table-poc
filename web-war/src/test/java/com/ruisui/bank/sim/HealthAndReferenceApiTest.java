package com.ruisui.bank.sim;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HealthAndReferenceApiTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthReturnsSuccessAndPocServiceList() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.respCode").value("0000"))
            .andExpect(jsonPath("$.data.status").value("UP"))
            .andExpect(jsonPath("$.data.services[0]").value("SYSHEALTH"));
    }

    @Test
    void dictionaryQueryReturnsConfiguredBusinessType() throws Exception {
        mockMvc.perform(get("/api/dicts/BUSINESS_TYPE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].code").value("02102"))
            .andExpect(jsonPath("$.data[0].name").value("普通汇兑"));
    }

    @Test
    void bankQueryReturnsConfiguredReceiveBank() throws Exception {
        mockMvc.perform(get("/api/banks").queryParam("bankNo", "102290000002"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].bankNo").value("102290000002"))
            .andExpect(jsonPath("$.data[0].bankName").value("接收行名称"));
    }

    @Test
    void bankQuerySupportsKeywordFiltering() throws Exception {
        mockMvc.perform(get("/api/banks").queryParam("keyword", "接收"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].bankNo").value("102290000002"))
            .andExpect(jsonPath("$.data[0].bankName").value("接收行名称"));
    }
}
