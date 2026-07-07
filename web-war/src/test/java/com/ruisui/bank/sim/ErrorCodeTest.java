package com.ruisui.bank.sim;

import com.ruisui.bank.sim.domain.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

    @Test
    void containsSpecAlignedInfrastructureAndUnknownErrorCodes() {
        assertThat(ErrorCode.PERSISTENCE_ERROR.code()).isEqualTo("4001");
        assertThat(ErrorCode.TUXEDO_TIMEOUT.code()).isEqualTo("4002");
        assertThat(ErrorCode.TUXEDO_UNAVAILABLE.code()).isEqualTo("4003");
        assertThat(ErrorCode.UNKNOWN_ERROR.code()).isEqualTo("9999");
    }
}
