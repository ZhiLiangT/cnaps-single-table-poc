package com.ruisui.bank.sim.api.dto;

import java.time.LocalDate;

public record HeaderContext(String requestId, String operatorNo, String branchNo, LocalDate workDate, String channel) {
}
