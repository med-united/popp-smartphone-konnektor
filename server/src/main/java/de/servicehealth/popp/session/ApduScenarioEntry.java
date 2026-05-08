package de.servicehealth.popp.session;

import java.util.concurrent.CompletableFuture;

public record ApduScenarioEntry(
    String correlationId, String payload, CompletableFuture<String> future) {}
