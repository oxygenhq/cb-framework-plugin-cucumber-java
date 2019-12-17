package io.cloudbeat.cucumber;

import java.util.List;
import java.util.Map;

public class ResultModel extends TestResultBase {
    public Map<String, String> options;
    public Map<String, String> capabilities;
    public Map<String, String> metadata;
    public Map<String, String> environmentVariables;
    public String instanceId;
    public int totalCases;
    public FailureModel failure;
    public List<SuiteModel> suites;
    public String runId;
}
