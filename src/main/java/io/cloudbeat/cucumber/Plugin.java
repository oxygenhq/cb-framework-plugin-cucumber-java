package io.cloudbeat.cucumber;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import cucumber.api.*;
import cucumber.api.event.EmbedEvent;
import cucumber.api.event.EventHandler;
import cucumber.api.event.EventListener;
import cucumber.api.event.EventPublisher;
import cucumber.api.event.TestCaseStarted;
import cucumber.api.event.TestCaseFinished;
import cucumber.api.event.TestRunFinished;
import cucumber.api.event.TestSourceRead;
import cucumber.api.event.TestStepFinished;
import cucumber.api.event.TestStepStarted;
import gherkin.ast.Background;
import gherkin.ast.Feature;
import gherkin.ast.ScenarioDefinition;
import gherkin.ast.Step;
import gherkin.deps.net.iharder.Base64;
import gherkin.pickles.Argument;
import gherkin.pickles.PickleCell;
import gherkin.pickles.PickleRow;
import gherkin.pickles.PickleString;
import gherkin.pickles.PickleTable;
import gherkin.pickles.PickleTag;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Plugin implements EventListener {
    private String currentFeatureFile;
    private List<Map<String, Object>> featureMaps = new ArrayList<Map<String, Object>>();
    private List<Map<String, Object>> currentElementsList;
    private Map<String, Object> currentElementMap;
    private Map<String, Object> currentTestCaseMap;
    private List<Map<String, Object>> currentStepsList;
    private Map<String, Object> currentStepOrHookMap;
    private Map<String, Object> currentBeforeStepHookList = new HashMap<String, Object>();
    private final TestSourcesModel testSources = new TestSourcesModel();
    private PayloadModel payload;
    private ResultModel result;
    private String testMonitorStatusUrl;
    private String testMonitorToken;
    private boolean isInitialized = false;
    private int currentCaseIndex = 1;
    private final static String TEST_RESULTS_FILENAME = ".CB_TEST_RESULTS";
    private final static String ERR_CUCUMBER_ERROR = "CUCUMBER_ERROR";

    private EventHandler<TestSourceRead> testSourceReadHandler = event -> handleTestSourceRead(event);
    private EventHandler<TestCaseStarted> caseStartedHandler = event -> handleTestCaseStarted(event);
    private EventHandler<TestCaseFinished> caseFinishedHandler = event -> handleTestCaseFinished(event);
    private EventHandler<TestStepStarted> stepStartedHandler = event -> handleTestStepStarted(event);
    private EventHandler<TestStepFinished> stepFinishedHandler = event -> handleTestStepFinished(event);
    private EventHandler<TestRunFinished> runFinishedHandler = event -> finishReport();
    private EventHandler<EmbedEvent> embedEventhandler = event -> handleEmbed(event);

    @SuppressWarnings("WeakerAccess")
    public Plugin(String arg) {
        String payloadpath = System.getProperty("payloadpath");
        String testmonitorurl = System.getProperty("testmonitorurl");
        testMonitorToken = System.getProperty("testmonitortoken");

        if (payloadpath != null && testmonitorurl != null && testMonitorToken != null) {
            testMonitorStatusUrl = testmonitorurl + "/status";

            try {
                payload = PayloadModel.Load(payloadpath);
                // pre-init results object
                result = new ResultModel();
                result.runId = payload.runId;
                result.instanceId = payload.instanceId;
                result.capabilities = payload.capabilities;
                result.metadata = payload.metadata;
                result.environmentVariables = payload.environmentVariables;
                result.startTime = new Date();

                if (result.capabilities.containsKey("browserName")) {
                    // remove "technology" prefix from the browserName. old CB version uses technology.browser as browserName
                    // FIXME: this should be removed once CB backend is adapted to send only the browser name without technology prefix.
                    String browserName = result.capabilities.get("browserName");
                    int browserNameIdx = browserName.indexOf('.');
                    if (browserNameIdx > 0)
                        browserName = browserName.substring(browserNameIdx + 1);
                    System.setProperty("browserName", browserName);
                } else {
                    logError("Plugin will be disabled. browserName is not specified in capabilities.");
                }

                isInitialized = true;
            } catch (Exception e) {
                logError("Plugin will be disabled. Unable to read/deserialize payload file.", e);
            }
        } else {
            logInfo("Plugin will be disabled. One of payloadpath, testmonitorurl, or testmonitortoken parameters is missing.");
        }
    }

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        if (!isInitialized)
            return;
        publisher.registerHandlerFor(TestSourceRead.class, testSourceReadHandler);
        publisher.registerHandlerFor(TestCaseStarted.class, caseStartedHandler);
        publisher.registerHandlerFor(TestCaseFinished.class, caseFinishedHandler);
        publisher.registerHandlerFor(TestStepStarted.class, stepStartedHandler);
        publisher.registerHandlerFor(TestStepFinished.class, stepFinishedHandler);
        publisher.registerHandlerFor(EmbedEvent.class, embedEventhandler);
        publisher.registerHandlerFor(TestRunFinished.class, runFinishedHandler);
    }
    private void handleTestSourceRead(TestSourceRead event) {
        testSources.addTestSourceReadEvent(event.uri, event);
    }

    private void handleTestCaseStarted(TestCaseStarted event) {
        if (currentFeatureFile == null || !currentFeatureFile.equals(event.testCase.getUri())) {
            currentFeatureFile = event.testCase.getUri();
            Map<String, Object> currentFeatureMap = createFeatureMap(event.testCase);
            featureMaps.add(currentFeatureMap);
            currentElementsList = (List<Map<String, Object>>) currentFeatureMap.get("elements");
        }
        currentTestCaseMap = createTestCase(event.testCase);
        if (testSources.hasBackground(currentFeatureFile, event.testCase.getLine())) {
            currentElementMap = createBackground(event.testCase);
            currentElementsList.add(currentElementMap);
        } else {
            currentElementMap = currentTestCaseMap;
        }
        currentElementsList.add(currentTestCaseMap);
        currentStepsList = (List<Map<String, Object>>) currentElementMap.get("steps");
    }

    private void handleTestCaseFinished(TestCaseFinished event) {
        boolean isPassed = true;
        for (Map<String, Object> step : currentStepsList) {
            Map<String, Object> result = (Map<String, Object>)step.get("result");
            if (result != null && !result.get("status").equals("passed"))
                isPassed = false;
        }

        StatusModel status = new StatusModel();

        status.status = StatusModel.Statuses.Running.getValue();
        status.instanceId = payload.instanceId;
        status.runId = payload.runId;
        status.caze = new StatusModel.CaseStatus();
        status.progress = (float)currentCaseIndex / payload.cases.size();

        String cucumberId = getCucumberScenarioId(event.testCase.getScenarioDesignation());

        PayloadModel.Case caseDefinition = payload.cases.get(cucumberId);
        if (caseDefinition == null) {
            logError("Cannot find matching case in the payload: " + cucumberId + ". Test case status won't be reported.");
            return;
        }

        status.caze.id = caseDefinition.id;
        status.caze.order = currentCaseIndex;
        status.caze.progress = 1;
        status.caze.name = event.testCase.getName();
        status.caze.iterationsFailed = isPassed ? 0 : 1;
        status.caze.iterationsPassed = isPassed ? 1 : 0;

        currentCaseIndex++;

        if (report(testMonitorStatusUrl, status))
            logInfo("Status report for '" + cucumberId + "' has been sent");
    }

    private void handleTestStepStarted(TestStepStarted event) {
        if (event.testStep instanceof PickleStepTestStep) {
            PickleStepTestStep testStep = (PickleStepTestStep) event.testStep;
            if (isFirstStepAfterBackground(testStep)) {
                currentElementMap = currentTestCaseMap;
                currentStepsList = (List<Map<String, Object>>) currentElementMap.get("steps");
            }
            currentStepOrHookMap = createTestStep(testStep);
            //add beforeSteps list to current step
            if (currentBeforeStepHookList.containsKey(HookType.Before.toString())) {
                currentStepOrHookMap.put(HookType.Before.toString(), currentBeforeStepHookList.get(HookType.Before.toString()));
                currentBeforeStepHookList.clear();
            }
            currentStepsList.add(currentStepOrHookMap);
        } else if(event.testStep instanceof HookTestStep) {
            HookTestStep hookTestStep = (HookTestStep) event.testStep;
            currentStepOrHookMap = createHookStep(hookTestStep);
            addHookStepToTestCaseMap(currentStepOrHookMap, hookTestStep.getHookType());
        } else {
            throw new IllegalStateException();
        }
    }

    private void handleEmbed(EmbedEvent event) {
        addEmbeddingToHookMap(event.data, event.mimeType);
    }

    private void handleTestStepFinished(TestStepFinished event) {
        currentStepOrHookMap.put("match", createMatchMap(event.testStep, event.result));
        currentStepOrHookMap.put("result", createResultMap(event.result));
    }

    private void finishReport() {
        result.endTime = new Date();
        result.duration = (result.endTime.getTime() -  result.startTime.getTime()) / 1000L;

        result.suites = new ArrayList<>();

        SuiteModel suite = new SuiteModel();
        result.suites.add(suite);

        suite.cases = new ArrayList<>();

        for (Map<String, Object> feature : featureMaps) {
            List<Map<String, Object>> scenarios = (List<Map<String, Object>>)feature.get("elements");

            for (Map<String, Object> scenario : scenarios) {
                CaseModel caze = new CaseModel();
                suite.cases.add(caze);

                String cucumberId = (String)scenario.get("cucumberId");

                PayloadModel.Case caseDefinition = payload.cases.get(cucumberId);
                if (caseDefinition == null) {
                    logError("Cannot find matching case in the payload: " + cucumberId + ". Test case result for this case won't be included in the report.");
                    break;
                }

                caze.id = caseDefinition.id;
                caze.name = (String)scenario.get("name");


                caze.iterationNum = 1;

                caze.steps = new ArrayList<>();

                boolean isSuccess = true;
                short order = 0;
                for (Map<String, Object> cucStep : (List<Map<String, Object>>)scenario.get("steps")) {
                    Map<String, Object> cucStepResult = (Map<String, Object>)cucStep.get("result");

                    FailureModel failure = null;

                    boolean stepStatus = cucStepResult.get("status").equals("passed");
                    if (!stepStatus) {
                        isSuccess = false;
                        failure = new FailureModel();
                        failure.type = ERR_CUCUMBER_ERROR;
                        // error_message won't be always present. For example it's not present on "skipped" (e.g. unimplemented) steps
                        if (cucStepResult.containsKey("error_message")){
                            failure.message = (String)cucStepResult.get("error_message");
                        }
                        else
                            failure.message = "See console log for more details";
                    }

                    StepModel step = new StepModel();

                    step.name = (String)cucStep.get("name");
                    step.order = order;
                    step.status = stepStatus ? ResultStatus.Passed : ResultStatus.Failed;
                    step.transaction = step.name;

                    if (!isSuccess) {
                        if (currentStepOrHookMap.containsKey("embeddings")) {
                            for (Map<String, String> embedding : (List<Map<String, String>>)currentStepOrHookMap.get("embeddings")) {
                                if (embedding.containsKey("mime_type") && embedding.containsKey("data") && "image/png".equals(embedding.get("mime_type"))) {
                                    step.screenShot = embedding.get("data");
                                    break;
                                }
                            }
                        } else {
                            step.screenShot = takeWebDriverScreenshot();
                        }
                    }

                    if (cucStepResult.containsKey("duration"))
                        step.duration = (long)((long)cucStepResult.get("duration") / 1000000d);

                    step.failure = failure;
                    caze.steps.add(step);

                    order++;
                }

                caze.status = isSuccess ? ResultStatus.Passed : ResultStatus.Failed;
            }
        }

        boolean isSuccess = true;
        for (CaseModel caze : suite.cases) {
            if (caze.status == ResultStatus.Failed) {
                        isSuccess = false;
            }
        }

        suite.status = isSuccess ? ResultStatus.Passed : ResultStatus.Failed;
        result.status = isSuccess ? ResultStatus.Passed : ResultStatus.Failed;

        ObjectMapper mapper = new ObjectMapper();
        String resultJson;
        try {
            resultJson = mapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            logError("Failed to serialize results.", e);
            return;
        }

        try {
            PrintWriter writer = new PrintWriter(TEST_RESULTS_FILENAME, "UTF-8");
            writer.write(resultJson);
            writer.close();
        } catch (FileNotFoundException | UnsupportedEncodingException e) {
            logError("Failed to create " + TEST_RESULTS_FILENAME, e);
        }
    }

    private Map<String, Object> createFeatureMap(TestCase testCase) {
        Map<String, Object> featureMap = new HashMap<String, Object>();
        featureMap.put("uri", testCase.getUri());
        featureMap.put("elements", new ArrayList<Map<String, Object>>());
        Feature feature = testSources.getFeature(testCase.getUri());
        if (feature != null) {
            featureMap.put("keyword", feature.getKeyword());
            featureMap.put("name", feature.getName());
            featureMap.put("description", feature.getDescription() != null ? feature.getDescription() : "");
            featureMap.put("line", feature.getLocation().getLine());
            featureMap.put("id", TestSourcesModel.convertToId(feature.getName()));
            featureMap.put("tags", feature.getTags());

        }
        return featureMap;
    }

    private Map<String, Object> createTestCase(TestCase testCase) {
        Map<String, Object> testCaseMap = new HashMap<String, Object>();
        testCaseMap.put("name", testCase.getName());
        testCaseMap.put("line", testCase.getLine());
        testCaseMap.put("type", "scenario");
        testCaseMap.put("cucumberId", getCucumberScenarioId(testCase.getScenarioDesignation()));
        TestSourcesModel.AstNode astNode = testSources.getAstNode(currentFeatureFile, testCase.getLine());
        if (astNode != null) {
            testCaseMap.put("id", TestSourcesModel.calculateId(astNode));
            ScenarioDefinition scenarioDefinition = TestSourcesModel.getScenarioDefinition(astNode);
            testCaseMap.put("keyword", scenarioDefinition.getKeyword());
            testCaseMap.put("description", scenarioDefinition.getDescription() != null ? scenarioDefinition.getDescription() : "");
        }
        testCaseMap.put("steps", new ArrayList<Map<String, Object>>());
        if (!testCase.getTags().isEmpty()) {
            List<Map<String, Object>> tagList = new ArrayList<Map<String, Object>>();
            for (PickleTag tag : testCase.getTags()) {
                Map<String, Object> tagMap = new HashMap<String, Object>();
                tagMap.put("name", tag.getName());
                tagList.add(tagMap);
            }
            testCaseMap.put("tags", tagList);
        }
        return testCaseMap;
    }

    private Map<String, Object> createBackground(TestCase testCase) {
        TestSourcesModel.AstNode astNode = testSources.getAstNode(currentFeatureFile, testCase.getLine());
        if (astNode != null) {
            Background background = TestSourcesModel.getBackgroundForTestCase(astNode);
            Map<String, Object> testCaseMap = new HashMap<String, Object>();
            testCaseMap.put("name", background.getName());
            testCaseMap.put("line", background.getLocation().getLine());
            testCaseMap.put("type", "background");
            testCaseMap.put("keyword", background.getKeyword());
            testCaseMap.put("description", background.getDescription() != null ? background.getDescription() : "");
            testCaseMap.put("steps", new ArrayList<Map<String, Object>>());
            return testCaseMap;
        }
        return null;
    }

    private boolean isFirstStepAfterBackground(PickleStepTestStep testStep) {
        TestSourcesModel.AstNode astNode = testSources.getAstNode(currentFeatureFile, testStep.getStepLine());
        if (astNode != null) {
            if (currentElementMap != currentTestCaseMap && !TestSourcesModel.isBackgroundStep(astNode)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> createTestStep(PickleStepTestStep testStep) {
        Map<String, Object> stepMap = new HashMap<String, Object>();
        stepMap.put("name", testStep.getStepText());
        stepMap.put("line", testStep.getStepLine());
        TestSourcesModel.AstNode astNode = testSources.getAstNode(currentFeatureFile, testStep.getStepLine());
        if (!testStep.getStepArgument().isEmpty()) {
            Argument argument = testStep.getStepArgument().get(0);
            if (argument instanceof PickleString) {
                stepMap.put("doc_string", createDocStringMap(argument));
            } else if (argument instanceof PickleTable) {
                stepMap.put("rows", createDataTableList(argument));
            }
        }
        if (astNode != null) {
            Step step = (Step) astNode.node;
            stepMap.put("keyword", step.getKeyword());
        }

        return stepMap;
    }

    private Map<String, Object> createDocStringMap(Argument argument) {
        Map<String, Object> docStringMap = new HashMap<String, Object>();
        PickleString docString = ((PickleString)argument);
        docStringMap.put("value", docString.getContent());
        docStringMap.put("line", docString.getLocation().getLine());
        docStringMap.put("content_type", docString.getContentType());
        return docStringMap;
    }

    private List<Map<String, Object>> createDataTableList(Argument argument) {
        List<Map<String, Object>> rowList = new ArrayList<Map<String, Object>>();
        for (PickleRow row : ((PickleTable)argument).getRows()) {
            Map<String, Object> rowMap = new HashMap<String, Object>();
            rowMap.put("cells", createCellList(row));
            rowList.add(rowMap);
        }
        return rowList;
    }

    private List<String> createCellList(PickleRow row) {
        List<String> cells = new ArrayList<String>();
        for (PickleCell cell : row.getCells()) {
            cells.add(cell.getValue());
        }
        return cells;
    }

    private Map<String, Object> createHookStep(HookTestStep hookTestStep) {
        return new HashMap<String, Object>();
    }

    private void addHookStepToTestCaseMap(Map<String, Object> currentStepOrHookMap, HookType hookType) {
        String hookName;
        if (hookType.toString().contains("after"))
            hookName = "after";
        else
            hookName = "before";


        Map<String, Object> mapToAddTo;
        switch (hookType) {
            case Before:
                mapToAddTo = currentTestCaseMap;
                break;
            case After:
                mapToAddTo = currentTestCaseMap;
                break;
            case BeforeStep:
                mapToAddTo = currentBeforeStepHookList;
                break;
            case AfterStep:
                mapToAddTo = currentStepsList.get(currentStepsList.size() - 1);
                break;
            default:
                mapToAddTo = currentTestCaseMap;
        }

        if (!mapToAddTo.containsKey(hookName)) {
            mapToAddTo.put(hookName, new ArrayList<Map<String, Object>>());
        }
        ((List<Map<String, Object>>)mapToAddTo.get(hookName)).add(currentStepOrHookMap);
    }

    private void addEmbeddingToHookMap(byte[] data, String mimeType) {
        if (!currentStepOrHookMap.containsKey("embeddings")) {
            currentStepOrHookMap.put("embeddings", new ArrayList<Map<String, Object>>());
        }
        Map<String, Object> embedMap = createEmbeddingMap(data, mimeType);
        ((List<Map<String, Object>>)currentStepOrHookMap.get("embeddings")).add(embedMap);
    }

    private Map<String, Object> createEmbeddingMap(byte[] data, String mimeType) {
        Map<String, Object> embedMap = new HashMap<String, Object>();
        embedMap.put("mime_type", mimeType);
        embedMap.put("data", Base64.encodeBytes(data));
        return embedMap;
    }

    private Map<String, Object> createMatchMap(TestStep step, Result result) {
        Map<String, Object> matchMap = new HashMap<String, Object>();
        if(step instanceof PickleStepTestStep) {
            PickleStepTestStep testStep = (PickleStepTestStep) step;
            if (!testStep.getDefinitionArgument().isEmpty()) {
                List<Map<String, Object>> argumentList = new ArrayList<Map<String, Object>>();
                for (cucumber.api.Argument argument : testStep.getDefinitionArgument()) {
                    Map<String, Object> argumentMap = new HashMap<String, Object>();
                    if (argument.getValue() != null) {
                        argumentMap.put("val", argument.getValue());
                        argumentMap.put("offset", argument.getStart());
                    }
                    argumentList.add(argumentMap);
                }
                matchMap.put("arguments", argumentList);
            }
        }
        if (!result.is(Result.Type.UNDEFINED)) {
            matchMap.put("location", step.getCodeLocation());
        }
        return matchMap;
    }

    private Map<String, Object> createResultMap(Result result) {
        Map<String, Object> resultMap = new HashMap<String, Object>();
        resultMap.put("status", result.getStatus().lowerCaseName());
        if (result.getErrorMessage() != null) {
            resultMap.put("error_message", result.getErrorMessage());
        }
        if (result.getDuration() != null && result.getDuration() != 0) {
            resultMap.put("duration", result.getDuration());
        }
        return resultMap;
    }

    private String getCucumberScenarioId(String caseDesignation) {
        String[] tokens = caseDesignation.split("/");
        return tokens[tokens.length-1];
    }

    private String takeWebDriverScreenshot() {
        WebDriver driver = CucumberRunner.getWebDriver();
        if (driver == null || !(driver instanceof TakesScreenshot))
            return null;
        return ((TakesScreenshot)driver).getScreenshotAs(OutputType.BASE64);
    }

    private String getEmbeddedScreenshot(List<Map<String, String>> embeddings) {
        for (Map<String, String> embedding : embeddings) {
            if (embedding.containsKey("mime_type") && embedding.containsKey("data") && "image/png".equals(embedding.get("mime_type"))) {
                return embedding.get("data");
            }
        }
        return null;
    }

    private boolean report(String endpointUrl, Object data) {
        HttpURLConnection http = null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(data);
            byte[] out = json.getBytes(StandardCharsets.UTF_8);
            int length = out.length;

            URL url = new URL(endpointUrl);
            URLConnection con = url.openConnection();
            http = (HttpURLConnection) con;
            http.setRequestMethod("POST");
            http.setRequestProperty("Authorization", "Bearer " + testMonitorToken);
            http.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            http.setRequestProperty("Connection", "Close");
            http.setDoOutput(true);
            http.setFixedLengthStreamingMode(length);
            http.connect();
            try (OutputStream os = http.getOutputStream()) {
                os.write(out);
                os.flush();
            }

            int responseCode = http.getResponseCode();
            if (responseCode < 200 || responseCode > 299) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(http.getInputStream()))) {
                    String inputLine;
                    StringBuffer response = new StringBuffer();
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    logError("Unable to report to " + endpointUrl + " : " + responseCode + " - " + response.toString());
                }
                return false;
            }


        } catch (Exception e) {
            logError("Unable to report to " + endpointUrl, e);
            return false;
        } finally {
            if (http != null)
                http.disconnect();
        }

        return true;
    }

    private void logError(String message) {
        System.err.println("[CloudBeat] " + message);
    }

    private void logError(String message, Exception e) {
        System.err.println("[CloudBeat] " + message);
        e.printStackTrace();
    }

    private void logInfo(String message) {
        System.out.println("[CloudBeat] " + message);
    }
}
