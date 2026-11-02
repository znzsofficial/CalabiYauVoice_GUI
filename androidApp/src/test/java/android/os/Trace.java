package android.os;

/**
 * Compose's headless runtime tests need no device trace recorder. Stub only trace markers,
 * not Android API behaviour generally (unitTests.returnDefaultValues stays disabled).
 */
public final class Trace {
    private Trace() {}
    public static void beginSection(String name) {}
    public static void endSection() {}
    public static boolean isEnabled() { return false; }
}
