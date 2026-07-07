package com.ruisui.bank.sim.domain;

public enum LastAction {
    CREATE("CREATE"),
    UPDATE("UPDATE"),
    DELETE("DELETE"),
    REVIEW_PASS("REVIEW_PASS"),
    REVIEW_RETURN("REVIEW_RETURN");

    private final String code;

    LastAction(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
