package com.rota.documents.web;

import com.rota.documents.internal.DocumentService;
import com.rota.documents.internal.DocumentService.CreateDocumentCommand;
import com.rota.documents.internal.DocumentService.UpdateDocumentCommand;
import com.rota.documents.web.DocumentDtos.CreateDocumentRequest;
import com.rota.documents.web.DocumentDtos.DocumentResponse;
import com.rota.documents.web.DocumentDtos.UpdateDocumentRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Document CRUD (plan task 2.3). Role floors via the hierarchy (§8.4): reads need viewer,
 * writes need editor, destructive document-level operations need admin.
 */
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    @PreAuthorize("hasRole('viewer')")
    public List<DocumentResponse> listDocuments() {
        return documentService.list().stream().map(DocumentResponse::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('editor')")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse createDocument(@Valid @RequestBody CreateDocumentRequest request) {
        return DocumentResponse.from(documentService.create(new CreateDocumentCommand(
                request.name(), request.slug(), request.description(),
                request.visibility(), request.initialVersionLabel())));
    }

    @GetMapping("/{documentId}")
    @PreAuthorize("hasRole('viewer')")
    public DocumentResponse getDocument(@PathVariable UUID documentId) {
        return DocumentResponse.from(documentService.get(documentId));
    }

    @PatchMapping("/{documentId}")
    @PreAuthorize("hasRole('editor')")
    public DocumentResponse updateDocument(@PathVariable UUID documentId,
                                           @Valid @RequestBody UpdateDocumentRequest request) {
        return DocumentResponse.from(documentService.update(documentId, new UpdateDocumentCommand(
                request.name(), request.slug(), request.description(),
                request.visibility(), request.branding())));
    }

    @DeleteMapping("/{documentId}")
    @PreAuthorize("hasRole('admin')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDocument(@PathVariable UUID documentId) {
        documentService.delete(documentId);
    }
}
