package org.hyvote.plugins.votifier.http;

import com.hypixel.hytale.server.core.HytaleServer;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hyvote.plugins.votifier.HytaleVotifierPlugin;
import org.hyvote.plugins.votifier.event.VoteEvent;
import org.hyvote.plugins.votifier.vote.Vote;

import java.io.IOException;
import java.util.logging.Level;

/**
 * HTTP endpoint for testing vote flow without encrypted payloads.
 *
 * <p>Endpoint: GET /Hyvote/HytaleVotifier/test</p>
 *
 * <p>Allows admins to test vote processing and event firing from a browser or curl.
 * Creates and fires a real VoteEvent that other plugins can listen to.</p>
 *
 * <p>Query parameters:</p>
 * <ul>
 *   <li>username (required) - the player username to create test vote for</li>
 *   <li>serviceName (optional, default "TestService") - the voting site identifier</li>
 *   <li>address (optional, default request remote address) - the voter's IP address</li>
 * </ul>
 *
 * <p>Response codes:</p>
 * <ul>
 *   <li>200 OK - Test vote created and fired successfully</li>
 *   <li>400 Bad Request - Missing required username parameter</li>
 *   <li>500 Internal Server Error - Unexpected server error</li>
 * </ul>
 */
public class TestVoteServlet extends HttpServlet {

    private final HytaleVotifierPlugin plugin;

    public TestVoteServlet(HytaleVotifierPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");

        try {
            // Extract and validate required username parameter
            String username = req.getParameter("username");
            if (username == null || username.isBlank()) {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing required parameter: username");
                return;
            }

            // Extract optional parameters with defaults
            String serviceName = req.getParameter("serviceName");
            if (serviceName == null || serviceName.isBlank()) {
                serviceName = "TestService";
            }

            String address = req.getParameter("address");
            if (address == null || address.isBlank()) {
                address = req.getRemoteAddr();
            }

            long timestamp = System.currentTimeMillis();

            // Create Vote record
            Vote vote = new Vote(serviceName, username, address, timestamp);

            // Fire VoteEvent
            VoteEvent voteEvent = new VoteEvent(plugin, vote);
            HytaleServer.get().getEventBus().dispatchFor(VoteEvent.class, plugin.getClass()).dispatch(voteEvent);

            // Log if debug enabled
            if (plugin.getConfig().debug()) {
                plugin.getLogger().at(Level.INFO).log("Test vote fired: service=%s, username=%s, address=%s",
                        vote.serviceName(), vote.username(), vote.address());
            }

            // Return success response with vote details
            String json = String.format(
                    "{\"status\": \"ok\", \"message\": \"Test vote fired for %s\", \"vote\": {\"serviceName\": \"%s\", \"username\": \"%s\", \"address\": \"%s\", \"timestamp\": %d}}",
                    username, vote.serviceName(), vote.username(), vote.address(), vote.timestamp()
            );

            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().println(json);

        } catch (Exception e) {
            plugin.getLogger().at(Level.SEVERE).withCause(e).log("Failed to process test vote request");
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error");
        }
    }

    private void sendError(HttpServletResponse resp, int statusCode, String message) throws IOException {
        resp.setStatus(statusCode);
        resp.getWriter().println(String.format("{\"status\": \"error\", \"message\": \"%s\"}", message));
    }
}
