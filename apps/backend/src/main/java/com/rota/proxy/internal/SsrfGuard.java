package com.rota.proxy.internal;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * SSRF protection (plan §13.1): only http/https to PUBLIC hosts. The target host is resolved
 * and every returned address must be public — loopback, any-local, link-local, site-local
 * (10/8, 172.16/12, 192.168/16, 169.254/16, ::1, fc00::/7) and multicast are blocked.
 *
 * <p>Note (v1): this validates at resolution time; a hostile DNS could rebind between this
 * check and the actual connection. Redirect following is disabled by the proxy to limit the
 * blast radius. Connection-pinning to the validated IP is a future hardening (Phase 18).
 */
@Component
public class SsrfGuard {

    private final ProxyProperties properties;

    public SsrfGuard(ProxyProperties properties) {
        this.properties = properties;
    }

    /** @throws SsrfBlockedException if the URL must not be called */
    public void validate(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new SsrfBlockedException("Only http and https targets are allowed.");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new SsrfBlockedException("Target URL has no host.");
        }
        if (!properties.isBlockPrivateNetworks()) {
            return; // local dev / test override
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new SsrfBlockedException("Target host could not be resolved: " + host);
        }
        for (InetAddress address : addresses) {
            if (!isPublic(address)) {
                throw new SsrfBlockedException(
                        "Target resolves to a non-public address and is blocked: " + host);
            }
        }
    }

    private boolean isPublic(InetAddress address) {
        if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        // IPv6 unique-local fc00::/7 (Java has no isSiteLocal for ULA).
        if (bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc) {
            return false;
        }
        // IPv4 100.64.0.0/10 (carrier-grade NAT) — not "site local" but not routable either.
        if (bytes.length == 4) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            if (first == 100 && second >= 64 && second <= 127) {
                return false;
            }
        }
        return true;
    }
}
