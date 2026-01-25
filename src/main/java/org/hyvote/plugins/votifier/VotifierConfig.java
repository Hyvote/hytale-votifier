package org.hyvote.plugins.votifier;

/**
 * Configuration for HytaleVotifier plugin.
 *
 * @param debug        Whether to enable debug logging (default false).
 * @param keyPath      Subdirectory for RSA keys relative to plugin config directory (default "keys").
 * @param voteMessage  Configuration for vote notification toast messages.
 */
public record VotifierConfig(boolean debug, String keyPath, VoteMessageConfig voteMessage) {

    /**
     * Returns a VotifierConfig with default values.
     *
     * @return default configuration
     */
    public static VotifierConfig defaults() {
        return new VotifierConfig(false, "keys", VoteMessageConfig.defaults());
    }
}
