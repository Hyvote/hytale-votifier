# Phase 3 Discovery: HTTP Endpoints

## Research Topic
WebServer plugin servlet registration via addServlet(), jakarta.servlet.http patterns

## Discovery Level
Level 2 - Standard Research (external API integration with WebServer plugin)

## Findings

### WebServer Plugin Integration Pattern

From the Query Plugin reference implementation:

**1. Dependency Lookup:**
```java
var plugin = PluginManager.get().getPlugin(
    new PluginIdentifier("Nitrado", "WebServer"));

if (!(plugin instanceof WebServerPlugin webServer)) {
    return;
}
this.webServerPlugin = webServer;
```

**2. Servlet Registration:**
```java
webServerPlugin.addServlet(this, "", new QueryServlet(templateEngine));
```
- First arg: Plugin instance (this)
- Second arg: Path spec (empty = /<Group>/<Name>, or "/path" for /<Group>/<Name>/path)
- Third arg: HttpServlet instance
- Optional varargs: Filter instances

**3. Cleanup in Shutdown:**
```java
@Override
protected void shutdown() {
    webServerPlugin.removeServlets(this);
}
```

### addServlet Method Signature
```java
public void addServlet(@Nonnull PluginBase plugin, String pathSpec,
                       HttpServlet servlet, Filter ...filters)
    throws IllegalPathSpecException
```

### Servlet Implementation Pattern
```java
public class MyServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
        throws IOException {
        resp.setContentType("application/json");
        resp.getWriter().println("{...}");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
        throws IOException {
        // Read request body
        var reader = req.getReader();
        // Process and respond
        resp.setStatus(HttpServletResponse.SC_OK);
    }
}
```

### Endpoint URLs
For HytaleVotifier (Group: "Hyvote", Name: "HytaleVotifier"):
- Base path: `/Hyvote/HytaleVotifier`
- Empty pathSpec: `/Hyvote/HytaleVotifier`
- "/vote" pathSpec: `/Hyvote/HytaleVotifier/vote`
- "/status" pathSpec: `/Hyvote/HytaleVotifier/status`

### Permission Annotations (Optional)
```java
@RequirePermissions(
    mode = RequirePermissions.Mode.ANY,
    value = { "my.permission.node" }
)
protected void doGet(...)
```

## Required Imports
```java
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import net.nitrado.hytale.plugins.webserver.WebServerPlugin;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
```

## Don't Hand-Roll
- HTTP server implementation - use WebServer plugin
- Path routing - use pathSpec parameter
- Content negotiation - available via RequestUtils (optional)
- Template rendering - available via TemplateEngine (optional, not needed for JSON-only)

## Architecture Decision
For HytaleVotifier, we need:
1. POST `/vote` - Receive encrypted vote payload (returns 200/400/500)
2. GET `/status` - Health check and public key info (returns JSON)

Both endpoints are simple JSON APIs - no HTML rendering needed.

## Sources
- https://github.com/nitrado/hytale-plugin-query
- https://github.com/nitrado/hytale-plugin-webserver
