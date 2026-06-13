package com.rota.proxy.web;

import com.rota.proxy.internal.ProxyService;
import com.rota.proxy.internal.ProxyService.ExecuteCommand;
import com.rota.proxy.internal.ProxyService.ExecuteResult;
import com.rota.proxy.web.ProxyDtos.ExecuteRequest;
import com.rota.proxy.web.ProxyDtos.ExecuteResponse;
import com.rota.proxy.web.ProxyDtos.HistoryEntryResponse;
import com.rota.proxy.web.ProxyDtos.QuotaResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Try It proxy API (plan §13.1 Mode A). Viewer floor — anyone who can see an endpoint may try
 * it; cross-tenant consumer {@code can_try} enforcement arrives with the Consumer Portal (Faz 9).
 */
@RestController
@RequestMapping("/api/v1")
public class ProxyController {

    private final ProxyService proxyService;

    public ProxyController(ProxyService proxyService) {
        this.proxyService = proxyService;
    }

    @PostMapping("/proxy/execute")
    @PreAuthorize("hasRole('viewer')")
    public ExecuteResponse execute(@Valid @RequestBody ExecuteRequest request) {
        ExecuteResult result = proxyService.execute(new ExecuteCommand(
                request.endpointId(), request.environmentId(), request.pathParams(),
                request.queryParams(), request.headers(), request.body()));
        return new ExecuteResponse(result.status(), result.latencyMs(), result.headers(),
                result.body(), result.truncated());
    }

    @GetMapping("/proxy/quota")
    @PreAuthorize("hasRole('viewer')")
    public QuotaResponse quota() {
        return new QuotaResponse(proxyService.remainingQuota(), proxyService.dailyLimit());
    }

    @GetMapping("/endpoints/{endpointId}/try-it-history")
    @PreAuthorize("hasRole('viewer')")
    public List<HistoryEntryResponse> history(@PathVariable UUID endpointId) {
        return proxyService.recentHistory(endpointId, 20).stream()
                .map(HistoryEntryResponse::from).toList();
    }
}
