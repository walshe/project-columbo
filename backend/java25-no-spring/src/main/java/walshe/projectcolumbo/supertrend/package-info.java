/**
 * Logging convention: every class obtains its own logger via
 * {@code LoggerFactory.getLogger(ClassName.class)} (SLF4J API, {@link org.slf4j.Logger}),
 * bound at runtime to {@code slf4j-simple}. This is also what backs Javalin/Jetty's own
 * internal logging - without a binding present those logs are silently dropped. Output
 * formatting/level configuration lives in {@code simplelogger.properties} on the classpath.
 */
package walshe.projectcolumbo.supertrend;
