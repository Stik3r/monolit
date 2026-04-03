package org.monolites.monolit.models.dtos.callback;

public record CallbackPayloadEnvelope(
        String type,
        int version,
        Object data
) {
}
