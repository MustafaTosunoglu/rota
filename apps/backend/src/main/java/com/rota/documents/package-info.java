/**
 * API documents and their versions: CRUD, the draft → published → archived lifecycle and
 * per-version environments (plan §10, Phase 2). Version CONTENT (categories, endpoints)
 * lives in the {@code endpoints} module; the two cooperate through {@code documents.api}
 * (version guard) and the {@link com.rota.documents.api.DocumentVersionClonedEvent}.
 */
@ApplicationModule(displayName = "Documents")
package com.rota.documents;

import org.springframework.modulith.ApplicationModule;
