package com.ruisui.bank.sim.domain;

public enum LastAction {
    CREATE("CREATE");

    private final String code;

    LastAction(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
