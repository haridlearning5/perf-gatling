package perf.gatling;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

/**
 * Load model for {@code perf-test-app}. Default {@code http://127.0.0.1:9080} avoids Windows {@code
 * localhost} ↔ IPv6 quirks. Override with {@code -DbaseUrl=http://host:port}.
 */
public class PerfTestSimulation extends Simulation {

  private static String baseUrl() {
    return System.getProperty("baseUrl", "http://127.0.0.1:9080");
  }

  private final HttpProtocolBuilder httpProtocol =
      http.baseUrl(baseUrl())
          .acceptHeader("application/json")
          .userAgentHeader("Gatling/perf-gatling");

  private final ScenarioBuilder userJourney =
      scenario("User journey")
          .exec(http("GET /health").get("/health").check(status().is(200)))
          .pause(1, 2)
          .exec(
              http("GET /api/hello")
                  .get("/api/hello")
                  .queryParam("name", "gatling")
                  .check(status().is(200)))
          .pause(1, 2)
          .exec(
              http("GET /api/work")
                  .get("/api/work")
                  .queryParam("iterations", "2000")
                  .check(status().is(200)));

  {
    setUp(userJourney.injectOpen(rampUsers(12).during(18)))
        .protocols(httpProtocol)
        .assertions(
            global().successfulRequests().percent().gt(95.0),
            global().responseTime().percentile3().lt(10000));
  }
}
