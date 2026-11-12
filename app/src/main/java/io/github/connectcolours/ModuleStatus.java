package io.github.connectcolours;

public final class ModuleStatus {
    private ModuleStatus() {}

    // Replaced only when LSPosed loads the module into its own application.
    public static int frameworkApi() {
        return 0;
    }
}
