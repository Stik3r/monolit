package org.monolites.monolit.models.enums;

public enum CallbackPayloadType {
    MONTHLY_REMINDER_DONE("monthly_reminder_done"),
    MONTHLY_REMINDER_POSTPONE("monthly_reminder_postpone");

    private final String value;

    CallbackPayloadType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
