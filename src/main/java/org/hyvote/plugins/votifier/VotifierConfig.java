package org.hyvote.plugins.votifier;

import java.util.List;

/**
 * Configuration for HytaleVotifier plugin.
 *
 * @param debug          Whether to enable debug logging (default false).
 * @param keyPath        Subdirectory for RSA keys relative to plugin config directory (default "keys").
 * @param voteMessage    Configuration for vote notification toast messages.
 * @param broadcast      Configuration for server-wide vote broadcast announcements.
 * @param rewardCommands Array of commands to execute when a vote is received. Each command has a chance probability.
 */
public record VotifierConfig(boolean debug, String keyPath, VoteMessageConfig voteMessage, BroadcastConfig broadcast, List<RewardCommand> rewardCommands) {

    /**
     * Returns a VotifierConfig with default values.
     *
     * @return default configuration
     */
    public static VotifierConfig defaults() {
        return new VotifierConfig(false, "keys", VoteMessageConfig.defaults(), BroadcastConfig.defaults(), List.of(
                new RewardCommand(false, "give {username} Ingredient_Stick", 1.0),
                new RewardCommand(false, "give {username} Ingredient_Bar_Iron", 0.1)
        ));
    }
}
