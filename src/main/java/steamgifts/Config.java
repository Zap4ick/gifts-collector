package steamgifts;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * App settings: bundled config.properties, then the optional git-ignored local overrides, then
 * env vars. Validated on load, so a broken config fails fast with a clear message.
 */
@Slf4j
public final class Config {

    static final String LOCAL_CONFIG_FILE = "config.local.properties";
    private static final String CONFIG_RESOURCE = "config.properties";

    private static final String SITE_KEY = "site";
    private static final String COOKIE_KEY = "cookie";
    private static final String CF_CLEARANCE_KEY = "cf_clearance";
    private static final String CI_KEY = "ci";
    private static final String CHROME_PROFILE_DIR_KEY = "chrome_profile_dir";
    private static final String LOCAL_BROWSER_PORT_KEY = "local_browser_port";

    private final Properties properties;
    private final String site;
    private final RunMode runMode;

    private Config(Properties properties) {
        this.properties = properties;
        this.site = readSite();
        this.runMode = readRunMode();
        // CI mode injects the session cookie; check it before spending time on starting a browser
        if (runMode == RunMode.CI && cookie().isBlank()) {
            throw new IllegalStateException("No cookie has been read from props! Set env var '"
                    + COOKIE_KEY + "' or put it into " + LOCAL_CONFIG_FILE);
        }
    }

    public static Config load() {
        Properties properties = new Properties();
        try (InputStream in = Config.class.getClassLoader().getResourceAsStream(CONFIG_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(CONFIG_RESOURCE + " not found on the classpath");
            }
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Can't read " + CONFIG_RESOURCE, e);
        }

        // Optional git-ignored overrides (secrets live here, not in the committed file)
        Path localConfig = Path.of(LOCAL_CONFIG_FILE);
        if (Files.isRegularFile(localConfig)) {
            try (Reader reader = Files.newBufferedReader(localConfig)) {
                properties.load(reader);
            } catch (IOException e) {
                throw new UncheckedIOException("Can't read " + localConfig.toAbsolutePath(), e);
            }
            log.info("Loaded local overrides from {}", localConfig.toAbsolutePath());
        }

        overrideFromEnv(properties, COOKIE_KEY, COOKIE_KEY);
        overrideFromEnv(properties, CF_CLEARANCE_KEY, CF_CLEARANCE_KEY);
        overrideFromEnv(properties, "CI", CI_KEY);
        overrideFromEnv(properties, "CHROME_PROFILE_DIR", CHROME_PROFILE_DIR_KEY);
        overrideFromEnv(properties, "LOCAL_BROWSER_PORT", LOCAL_BROWSER_PORT_KEY);
        return new Config(properties);
    }

    /** An env var that is set (even to an empty string, which clears the value) wins over config files. */
    private static void overrideFromEnv(Properties properties, String envName, String key) {
        Optional.ofNullable(System.getenv(envName)).ifPresent(value -> properties.setProperty(key, value));
    }

    /** Site root, always with a trailing slash. */
    public String site() {
        return site;
    }

    public List<String> categoryPages() {
        return List.of(
                site + "giveaways/search?type=wishlist",
                site + "giveaways/search?type=recommended",
                site + "giveaways/search?dlc=true",
                site
        );
    }

    public RunMode runMode() {
        return runMode;
    }

    /** Running under CI (the CI env var is set). */
    public boolean isCi() {
        return properties.getProperty(CI_KEY) != null;
    }

    public String cookie() {
        return get(COOKIE_KEY);
    }

    public Optional<String> cfClearance() {
        return Optional.of(get(CF_CLEARANCE_KEY)).filter(v -> !v.isBlank());
    }

    public String chromeProfileDir() {
        return get(CHROME_PROFILE_DIR_KEY);
    }

    /** Only meaningful in {@link RunMode#ATTACH}, where it is validated to be a number. */
    public int localBrowserPort() {
        return Integer.parseInt(get(LOCAL_BROWSER_PORT_KEY));
    }

    private String get(String key) {
        return properties.getProperty(key, "").trim();
    }

    private String readSite() {
        String value = get(SITE_KEY);
        if (value.isEmpty()) {
            throw new IllegalStateException("'" + SITE_KEY + "' is not set in " + CONFIG_RESOURCE + " / " + LOCAL_CONFIG_FILE);
        }
        return value.endsWith("/") ? value : value + "/";
    }

    private RunMode readRunMode() {
        if (!chromeProfileDir().isEmpty()) {
            return RunMode.PROFILE;
        }
        String port = get(LOCAL_BROWSER_PORT_KEY);
        if (port.isEmpty()) {
            return RunMode.CI;
        }
        try {
            Integer.parseInt(port);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("'" + LOCAL_BROWSER_PORT_KEY + "' must be a port number, got '" + port + "'");
        }
        return RunMode.ATTACH;
    }
}
