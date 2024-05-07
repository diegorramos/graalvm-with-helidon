package io.diegorramos;

import io.helidon.config.Config;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;
import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;

import java.util.Collections;
import java.util.logging.Logger;

/**
 * A simple service to greet you. Examples:
 * <p>
 * Get default greeting message:
 * curl -X GET http://localhost:8080/simple-greet
 * <p>
 * The message is returned as a JSON object
 */
public class SimpleGreetService implements Service {

    private static final Logger LOGGER = Logger.getLogger(SimpleGreetService.class.getName());
    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());

    private final String greeting;

    SimpleGreetService(Config config) {
        greeting = config.get("app.greeting").asString().orElse("Ciao");
    }


    /**
     * A service registers itself by updating the routing rules.
     *
     * @param rules the routing rules.
     */
    @Override
    public void update(Routing.Rules rules) {
        rules.get("/", this::getDefaultMessageHandler);
    }

    /**
     * Return a worldly greeting message.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void getDefaultMessageHandler(ServerRequest request, ServerResponse response) {
        String msg = String.format("%s %s!", greeting, "World");
        LOGGER.info("Greeting message is " + msg);
        JsonObject returnObject = JSON.createObjectBuilder()
                .add("message", msg)
                .build();
        response.send(returnObject);
    }

}
