package com.rota.endpoints.web;

import com.rota.endpoints.internal.EndpointService;
import com.rota.endpoints.internal.EndpointService.CreateEndpointCommand;
import com.rota.endpoints.internal.EndpointService.ParameterCommand;
import com.rota.endpoints.internal.EndpointService.RequestBodyCommand;
import com.rota.endpoints.internal.EndpointService.ResponseCommand;
import com.rota.endpoints.internal.EndpointService.UpdateEndpointCommand;
import com.rota.endpoints.jpa.EndpointEntity;
import com.rota.endpoints.web.EndpointDtos.CreateEndpointRequest;
import com.rota.endpoints.web.EndpointDtos.CreateParameterRequest;
import com.rota.endpoints.web.EndpointDtos.CreateRequestBodyRequest;
import com.rota.endpoints.web.EndpointDtos.CreateResponseRequest;
import com.rota.endpoints.web.EndpointDtos.EndpointDetailResponse;
import com.rota.endpoints.web.EndpointDtos.EndpointSummaryResponse;
import com.rota.endpoints.web.EndpointDtos.ParameterResponse;
import com.rota.endpoints.web.EndpointDtos.RequestBodyResponse;
import com.rota.endpoints.web.EndpointDtos.ResponseResponse;
import com.rota.endpoints.web.EndpointDtos.UpdateEndpointRequest;
import com.rota.endpoints.web.EndpointDtos.UpdateParameterRequest;
import com.rota.endpoints.web.EndpointDtos.UpdateResponseRequest;
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

/** Endpoint CRUD with parameters / request bodies / responses as sub-resources. */
@RestController
@RequestMapping("/api/v1")
public class EndpointController {

    private final EndpointService endpointService;

    public EndpointController(EndpointService endpointService) {
        this.endpointService = endpointService;
    }

    // --- endpoints -----------------------------------------------------------------

    @GetMapping("/versions/{versionId}/endpoints")
    @PreAuthorize("hasRole('viewer')")
    public List<EndpointSummaryResponse> listEndpoints(@PathVariable UUID versionId) {
        return endpointService.list(versionId).stream().map(EndpointSummaryResponse::from).toList();
    }

    @PostMapping("/versions/{versionId}/endpoints")
    @PreAuthorize("hasRole('editor')")
    @ResponseStatus(HttpStatus.CREATED)
    public EndpointSummaryResponse createEndpoint(@PathVariable UUID versionId,
                                                  @Valid @RequestBody CreateEndpointRequest request) {
        return EndpointSummaryResponse.from(endpointService.create(versionId, new CreateEndpointCommand(
                request.categoryId(), request.method(), request.path(), request.summary(),
                request.descriptionMd(), request.authType(), request.authConfig(),
                request.sortOrder(), request.deprecated())));
    }

    @GetMapping("/endpoints/{endpointId}")
    @PreAuthorize("hasRole('viewer')")
    public EndpointDetailResponse getEndpoint(@PathVariable UUID endpointId) {
        EndpointEntity endpoint = endpointService.get(endpointId);
        return EndpointDetailResponse.from(endpoint,
                endpointService.listParameters(endpointId),
                endpointService.listRequestBodies(endpointId),
                endpointService.listResponses(endpointId));
    }

    @PatchMapping("/endpoints/{endpointId}")
    @PreAuthorize("hasRole('editor')")
    public EndpointSummaryResponse updateEndpoint(@PathVariable UUID endpointId,
                                                  @Valid @RequestBody UpdateEndpointRequest request) {
        return EndpointSummaryResponse.from(endpointService.update(endpointId, new UpdateEndpointCommand(
                request.categoryId(), request.clearCategory(), request.method(), request.path(),
                request.summary(), request.descriptionMd(), request.authType(), request.authConfig(),
                request.sortOrder(), request.mockEnabled(), request.deprecated())));
    }

    @DeleteMapping("/endpoints/{endpointId}")
    @PreAuthorize("hasRole('editor')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEndpoint(@PathVariable UUID endpointId) {
        endpointService.delete(endpointId);
    }

    // --- parameters ----------------------------------------------------------------

    @PostMapping("/endpoints/{endpointId}/parameters")
    @PreAuthorize("hasRole('editor')")
    @ResponseStatus(HttpStatus.CREATED)
    public ParameterResponse createParameter(@PathVariable UUID endpointId,
                                             @Valid @RequestBody CreateParameterRequest request) {
        return ParameterResponse.from(endpointService.addParameter(endpointId, new ParameterCommand(
                request.name(), request.location(), request.dataType(), request.required(),
                request.description(), request.defaultValue(), request.example(), request.sortOrder())));
    }

    @PatchMapping("/parameters/{parameterId}")
    @PreAuthorize("hasRole('editor')")
    public ParameterResponse updateParameter(@PathVariable UUID parameterId,
                                             @Valid @RequestBody UpdateParameterRequest request) {
        return ParameterResponse.from(endpointService.updateParameter(parameterId, new ParameterCommand(
                request.name(), request.location(), request.dataType(), request.required(),
                request.description(), request.defaultValue(), request.example(), request.sortOrder())));
    }

    @DeleteMapping("/parameters/{parameterId}")
    @PreAuthorize("hasRole('editor')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteParameter(@PathVariable UUID parameterId) {
        endpointService.deleteParameter(parameterId);
    }

    // --- request bodies ------------------------------------------------------------

    @PostMapping("/endpoints/{endpointId}/request-bodies")
    @PreAuthorize("hasRole('editor')")
    @ResponseStatus(HttpStatus.CREATED)
    public RequestBodyResponse createRequestBody(@PathVariable UUID endpointId,
                                                 @Valid @RequestBody CreateRequestBodyRequest request) {
        return RequestBodyResponse.from(endpointService.addRequestBody(endpointId, new RequestBodyCommand(
                request.contentType(), request.schemaJson(), request.exampleJson())));
    }

    @PatchMapping("/request-bodies/{requestBodyId}")
    @PreAuthorize("hasRole('editor')")
    public RequestBodyResponse updateRequestBody(@PathVariable UUID requestBodyId,
                                                 @Valid @RequestBody CreateRequestBodyRequest request) {
        return RequestBodyResponse.from(endpointService.updateRequestBody(requestBodyId, new RequestBodyCommand(
                request.contentType(), request.schemaJson(), request.exampleJson())));
    }

    @DeleteMapping("/request-bodies/{requestBodyId}")
    @PreAuthorize("hasRole('editor')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRequestBody(@PathVariable UUID requestBodyId) {
        endpointService.deleteRequestBody(requestBodyId);
    }

    // --- responses -----------------------------------------------------------------

    @PostMapping("/endpoints/{endpointId}/responses")
    @PreAuthorize("hasRole('editor')")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseResponse createResponse(@PathVariable UUID endpointId,
                                           @Valid @RequestBody CreateResponseRequest request) {
        return ResponseResponse.from(endpointService.addResponse(endpointId, new ResponseCommand(
                request.statusCode(), request.description(), request.contentType(),
                request.schemaJson(), request.exampleJson())));
    }

    @PatchMapping("/responses/{responseId}")
    @PreAuthorize("hasRole('editor')")
    public ResponseResponse updateResponse(@PathVariable UUID responseId,
                                           @Valid @RequestBody UpdateResponseRequest request) {
        return ResponseResponse.from(endpointService.updateResponse(responseId, new ResponseCommand(
                request.statusCode(), request.description(), request.contentType(),
                request.schemaJson(), request.exampleJson())));
    }

    @DeleteMapping("/responses/{responseId}")
    @PreAuthorize("hasRole('editor')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteResponse(@PathVariable UUID responseId) {
        endpointService.deleteResponse(responseId);
    }
}
