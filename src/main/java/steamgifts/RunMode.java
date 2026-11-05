package steamgifts;

/** How the browser is obtained. Determined once from config, see {@link Config#runMode()}. */
public enum RunMode {
    /** Launch Chrome with the user's real profile (chrome_profile_dir). */
    PROFILE,
    /** Attach to an already running Chrome via its debug port (local_browser_port). */
    ATTACH,
    /** Fresh Chrome with the session cookie injected; nobody at the console. */
    CI;

    /** A person is at the console and the browser already has their session. */
    public boolean isLocal() {
        return this != CI;
    }
}
