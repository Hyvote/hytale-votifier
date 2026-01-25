package org.hyvote.plugins.votifier;

import java.util.Collections;
import java.util.Map;

/**
 * Configuration for Votifier V2 protocol service tokens.
 * Maps vote site service names to their shared secret tokens for HMAC-SHA256 verification.
 *
 * @param voteSites Map of service names to their authentication tokens
 */
public record VoteSiteTokenConfig(Map<String, String> voteSites) {

    /**
     * Returns a VoteSiteTokenConfig with default values (empty map).
     *
     * @return default configuration with no services configured
     */
    public static VoteSiteTokenConfig defaults() {
        return new VoteSiteTokenConfig(Collections.emptyMap());
    }

    /**
     * Compact constructor that ensures immutability of the services map.
     */
    public VoteSiteTokenConfig {
        voteSites = voteSites != null ? Map.copyOf(voteSites) : Collections.emptyMap();
    }

    /**
     * Gets the token for a given service name.
     *
     * @param serviceName the service name to look up
     * @return the token, or null if not configured
     */
    public String getToken(String serviceName) {
        return voteSites.get(serviceName);
    }

    /**
     * Checks if V2 protocol is enabled (at least one service configured).
     *
     * @return true if at least one vote site token is configured
     */
    public boolean isV2Enabled() {
        return !voteSites.isEmpty();
    }
}
