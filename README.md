## CloudBeat plugin for Cucumber-Java

### Intro
This plugin allows executing Java based Cucumber tests using the CloudBeat platform.

### Building
`git clone https://github.com/oxygenhq/cb-framework-plugin-cucumber-java`  
`cd cb-framework-plugin-cucumber-java`  
`mvn install`  

### Usage
Add the plugin to your project. If you are using a maven based project, you can directly add this library as a dependency:
```xml
<dependency>  
  <groupId>io.cloudbeat.cucumber</groupId>  
  <artifactId>cb-plugin-cucumber</artifactId>  
  <version>0.1-SNAPSHOT</version>  
</dependency>
```

Add `io.cloudbeat.cucumber.Plugin` to Cucumber options and make sure the class annotated with `@RunWith(Cucumber.class)` extends `CucumberRunner`

```java
@RunWith(Cucumber.class)
@CucumberOptions(plugin = {"pretty", "io.cloudbeat.cucumber.Plugin:"})
public class RunCucumberTest extends CucumberRunner {
}
```

### Working with Selenium

When using Selenium it might be beneficiary to be able to take browser screenshots in case of failures.
This can be achieved in a three different ways. Please note that all 3 options are mutually exclusive.

1. By embedding screenshots manually from `@After()` method within the glue classes. Screenshot should be embedded as a Base64 string using the `image/png` mime type. See the examples below for more details.
2. By providing WebDriver instance to the plugin.
3. By providing WebDriver getter method to the plugin.

#### Embedding screenshots manually

```java
public class SeleniumDefs {
    private final WebDriver driver = new ChromeDriver();

    @Given("^I am on the Google search page$")
    public void I_visit_google() {
        ...
    }

    @When("^I search for \"(.*)\"$")
    public void search_for(String query) {
        ...
    }

    @Then("^the page title should start with \"(.*)\"$")
    public void checkTitle(String titleStartsWith) {
        ...
    }

    @After()
    public void tearDown(Scenario scenario) {
        if (scenario.isFailed()) {
            try {
                final byte[] screenshot = ((TakesScreenshot)driver).getScreenshotAs(OutputType.BYTES);
                scenario.embed(screenshot, "image/png");
            } catch (Exception e){
                System.err.println("Couldn't take screenshot" + e);
            }
        }
        driver.quit();
    }
}
```

#### Providing WebDriver instance
```java
@RunWith(Cucumber.class)
@CucumberOptions(plugin = {"pretty", "io.cloudbeat.cucumber.Plugin:"})
public class RunCucumberTest extends CucumberRunner {
    @BeforeClass
    public static void setUp() {
        WebDriver driver = ... // WebDriver initialization
        setWebDriver(driver);
    }
}
```

#### Providing WebDriver getter method
```java
@RunWith(Cucumber.class)
@CucumberOptions(plugin = {"pretty", "io.cloudbeat.cucumber.Plugin:"})
public class RunCucumberTest extends CucumberRunner {
    @BeforeClass
    public static void setUp() {
       WebDriverProvider provider = new WebDriverProvider();
       // getter should have WebDriver as its return type and shouldn't expect any arguments
       setWebDriverGetter(provider::getWebDriver);
    }
}

public class WebDriverProvider {
    public WebDriver getWebDriver() {
       return driver;
    }
}
```
