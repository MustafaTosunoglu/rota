package com.rota.documents.api;

/** A version environment to create on import (e.g. an OpenAPI server). */
public record EnvironmentSpec(String name, String baseUrl, boolean productionWarn) {
}
