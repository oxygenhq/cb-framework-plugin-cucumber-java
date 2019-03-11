
## CloudBeat plugin for Java Cucumber 

### Intro
CloudBeat plugin for Java based Cucumber projects.

### Configuration
Add the plugin to your project. For maven projects just add this dependency:
```xml
<dependency>  
  <groupId>io.cloudbeat.cucumber</groupId>  
  <artifactId>cb-plugin-cucumber</artifactId>  
  <version>0.1</version>  
</dependency>
```

Add `io.cloudbeat.cucumber.Plugin` to Cucumber options and make sure the class anotated with `@RunWith(Cucumber.class)` extends `CucumberRunner`

```java
@RunWith(Cucumber.class)
@CucumberOptions(plugin = {"pretty", "io.cloudbeat.cucumber.Plugin:"})
public class RunCucumberTest extends CucumberRunner {
}
```

### Working with Selenium

#### Embedding screenshots manually

```java
public class SeleniumDefs {
    private final WebDriver driver = new ChromeDriver();

    @Given("^I am on the Google search page$")
    public void I_visit_google() {
        driver.get("https:\\www.google.com");
    }

    @When("^I search for \"(.*)\"$")
    public void search_for(String query) {
        WebElement element = driver.findElement(By.name("q"));
        element.sendKeys(query);
        element.submit();
    }

    @Then("^the page title should start with \"(.*)\"$")
    public void checkTitle(String titleStartsWith) {
        new WebDriverWait(driver,10L).until(new ExpectedCondition<Boolean>() {
            public Boolean apply(WebDriver d) {
                return d.getTitle().toLowerCase().startsWith(titleStartsWith);
            }
        });
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
