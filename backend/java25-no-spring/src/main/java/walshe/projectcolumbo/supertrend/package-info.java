/**
 * Logging convention: every class obtains its own logger via
 * {@code System.getLogger(ClassName.class.getName())} (JDK built-in,
 * {@link java.lang.System.Logger}) — no SLF4J, no Logback, no wrapper class.
 * Output formatting/level configuration lives in {@code logging.properties}
 * on the classpath, consumed by the default {@code java.util.logging} backend.
 */
package walshe.projectcolumbo.supertrend;
