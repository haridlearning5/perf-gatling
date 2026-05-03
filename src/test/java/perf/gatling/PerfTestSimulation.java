package perf.gatling;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import java.time.Duration;

/**
 * Load model for {@code perf-test-app}. Default {@code http://127.0.0.1:9080} avoids Windows {@code
 * localhost} ↔ IPv6 quirks. Override with {@code -DbaseUrl=http://host:port}.
 *
 * <p>Target URL: JVM {@code -DbaseUrl}, else env {@code GATLING_BASE_URL}, else localhost. Prefer Maven
 * {@code -Dgatling.baseUrl=} (wired in pom) so the Gatling fork always sees the AWS host — a single merged
 * {@code -Dgatling.jvmArgs=... -DbaseUrl=...} string is parsed incorrectly by Netty and silently drops {@code baseUrl}.
 *
 * <p>Default closed workload ramps to <strong>100</strong> concurrent users, holds that level, and
 * loops heavy {@code /api/work}. Open model: {@code -Dperf.openModel=true}.
 *
 * <p>Tune: {@code -Dperf.constantConcurrent=80 -Dperf.workIterations=500000}. For visible CPU in
 * AWS, increase {@code perf.workIterations} before inflating user counts.
 */
public class PerfTestSimulation extends Simulation {

  private static String baseUrl() {
    String prop = System.getProperty("baseUrl");
    if (prop != null && !prop.isBlank()) {
      return stripTrailingSlash(prop.strip());
    }
    String env = System.getenv("GATLING_BASE_URL");
    if (env != null && !env.isBlank()) {
      return stripTrailingSlash(env.strip());
    }
    return stripTrailingSlash("http://127.0.0.1:9080");
  }

  private static String stripTrailingSlash(String u) {
    return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
  }

  private static int intProp(String key, int def) {
    String v = System.getProperty(key);
    if (v == null || v.isBlank()) {
      return def;
    }
    return Integer.parseInt(v.trim());
  }

  private static double doubleProp(String key, double def) {
    String v = System.getProperty(key);
    if (v == null || v.isBlank()) {
      return def;
    }
    return Double.parseDouble(v.trim());
  }

  private static boolean boolProp(String key, boolean def) {
    String v = System.getProperty(key);
    if (v == null || v.isBlank()) {
      return def;
    }
    return Boolean.parseBoolean(v.trim());
  }

  /** Open arrival-rate workload (historical behaviour). Default false = closed plateau load. */
  private static final boolean OPEN_MODEL = boolProp("perf.openModel", false);

  /** Users started during the ramp phase (open model only). */
  private static final int RAMP_USERS = intProp("perf.rampUsers", 60);

  /** Ramp length (seconds), open model. */
  private static final int RAMP_DURING_SEC = intProp("perf.rampDuringSec", 40);

  /** New sessions per second (open model sustained phase). */
  private static final double STEADY_RPS = doubleProp("perf.steadyRps", 10.0);

  /** Sustain phase duration (seconds), open model. */
  private static final int STEADY_SEC = intProp("perf.steadySec", 240);

  /** Concurrent users ramp (closed model), target count at end of ramp. */
  private static final int RAMP_CONCURRENT = intProp("perf.rampConcurrent", 100);

  /** Ramp duration (seconds), closed model. */
  private static final int RAMP_CONCURRENT_DUR_SEC = intProp("perf.rampConcurrentDurSec", 45);

  /** Steady concurrent users (closed model) — main lever for CPU %. */
  private static final int CONSTANT_CONCURRENT = intProp("perf.constantConcurrent", 100);

  /** How long to hold {@link #CONSTANT_CONCURRENT} (seconds), closed model. */
  private static final int CONSTANT_CONCURRENT_DUR_SEC =
      intProp("perf.constantConcurrentDurSec", 300);

  /**
   * {@code /api/work?iterations=} — server clamps to 2_000_000 — default uses full cap so JVM work
   * per request dominates (high RPS with low iterations can look “busy” in Gatling yet stay <5% CPU).
   */
  private static final int WORK_ITERATIONS = intProp("perf.workIterations", 2_000_000);

  private final HttpProtocolBuilder httpProtocol =
      http.baseUrl(baseUrl())
          .acceptHeader("application/json")
          .userAgentHeader("Gatling/perf-gatling")
          .shareConnections();

  /**
   * Closed users loop this until the injection step ends — no /health in the hot path (CloudWatch
   * should show a wide CPU plateau, not a single toothpick spike).
   */
  private final ScenarioBuilder cpuPlateau =
      scenario("cpu plateau")
          .repeat(intProp("perf.workRepeats", 5))
          .on(
              exec(
                      http("GET /api/work")
                          .get("/api/work")
                          .queryParam("iterations", Integer.toString(WORK_ITERATIONS))
                          .check(status().is(200)))
                  .pause(Duration.ZERO, Duration.ofMillis(25)));

  {
    var injection =
        OPEN_MODEL
            ? cpuPlateau.injectOpen(
                rampUsers(RAMP_USERS).during(RAMP_DURING_SEC),
                constantUsersPerSec(STEADY_RPS).during(Duration.ofSeconds(STEADY_SEC)))
            : cpuPlateau.injectClosed(
                rampConcurrentUsers(0)
                    .to(RAMP_CONCURRENT)
                    .during(Duration.ofSeconds(RAMP_CONCURRENT_DUR_SEC)),
                constantConcurrentUsers(CONSTANT_CONCURRENT)
                    .during(Duration.ofSeconds(CONSTANT_CONCURRENT_DUR_SEC)));

    setUp(injection)
        .protocols(httpProtocol)
        .assertions(
            global().successfulRequests().percent().gt(50.0),
            global().responseTime().percentile3().lt(480_000));
  }
}
