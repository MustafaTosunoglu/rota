package com.rota.documents.api;

import java.util.UUID;

/** Cross-module port letting the importer add environments to a draft version. */
public interface EnvironmentWriter {

    /** Adds an environment to the version, skipping silently if its name already exists. */
    void addIfAbsent(UUID versionId, EnvironmentSpec environment);
}
