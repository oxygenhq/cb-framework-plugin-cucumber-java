package io.cloudbeat.cucumber;

import org.openqa.selenium.WebDriver;

import java.util.function.Supplier;

public abstract class CucumberRunner {
    private static WebDriver _webDriver;
    private static Supplier<WebDriver> _webDriverGetter;

    protected static void setWebDriver(WebDriver webDriver) {
        _webDriver = webDriver;
    }

    protected static void setWebDriverGetter(Supplier<WebDriver> webDriverGetter) {
        _webDriverGetter = webDriverGetter;
    }

    public static WebDriver getWebDriver() {
        if (_webDriver != null)
            return _webDriver;
        else if (_webDriverGetter != null)
            return _webDriverGetter.get();
        return null;
    }
}
