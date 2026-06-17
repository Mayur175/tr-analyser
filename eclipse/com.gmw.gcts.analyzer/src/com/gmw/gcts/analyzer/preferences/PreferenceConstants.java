package com.gmw.gcts.analyzer.preferences;

/** Keys for Eclipse preference store (persisted per workspace). */
public final class PreferenceConstants {

    private PreferenceConstants() {}

    /** Base URL of the SAP system, e.g. https://my-system.hana.ondemand.com */
    public static final String PREF_SYSTEM_URL = "com.gmw.gcts.analyzer.systemUrl";

    /** SAP username for ICF authentication (stored in Eclipse secure storage). */
    public static final String PREF_USERNAME   = "com.gmw.gcts.analyzer.username";

    /** SAP password — stored in Eclipse SecurePreferences, NOT plain preference store. */
    public static final String PREF_PASSWORD   = "com.gmw.gcts.analyzer.password";

    /** HTTP connection timeout in seconds (default 30). */
    public static final String PREF_TIMEOUT_S  = "com.gmw.gcts.analyzer.timeoutSeconds";

    public static final int    DEFAULT_TIMEOUT  = 30;
}
