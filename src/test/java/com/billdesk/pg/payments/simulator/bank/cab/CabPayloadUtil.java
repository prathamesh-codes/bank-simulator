package com.billdesk.pg.payments.simulator.bank.cab;

import java.util.Map;
import java.util.stream.Collectors;

public final class CabPayloadUtil {

    private CabPayloadUtil() {
    }

    public static String buildPipePayload(
            Map<String, String> fields) {

        return fields.entrySet()
                .stream()
                .map(entry ->
                        entry.getKey()
                        + "="
                        + safe(entry.getValue())
                )
                .collect(
                        Collectors.joining("|")
                );
    }

    private static String safe(
            String value) {

        return value == null
                ? ""
                : value;
    }
}