package org.hyvote.plugins.votifier;

/**
 * Configuration for HytaleVotifier plugin.
 *
 * @param port    The port for vote reception (default 8192, standard Votifier port).
 *                Note: Actual HTTP port is controlled by WebServer plugin.
 * @param debug   Whether to enable debug logging (default false).
 * @param keyPath Subdirectory for RSA keys relative to plugin config directory (default "keys").
 */
public record VotifierConfig(int port, boolean debug, String keyPath) {

    /**
     * Returns a VotifierConfig with default values.
     *
     * @return default configuration
     */
    public static VotifierConfig defaults() {
        return new VotifierConfig(8192, false, "keys");
    }
}
