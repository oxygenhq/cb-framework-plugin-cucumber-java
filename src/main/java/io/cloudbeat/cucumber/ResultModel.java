package io.cloudbeat.cucumber;

import java.util.Date;
import java.util.List;
import java.util.Map;

public class ResultModel {

    public String runId;
    public String instanceId;
    public String nodeId;
    public Map<String, String> metadata;
    public Map<String, String> capabilities;
    public String environmentVariables;
    public Date startTime;
    public Date endTime;
    public double duration;
    public int retries;
    public boolean isSuccess;
    public int iterationsTotal;
    public int iterationsPassed;
    public int iterationsFailed;
    public int iterationsWarning;
    public String failure;
    public List<SuiteIteration> iterations;

    public static class SuiteIteration
    {
        public int iterationNum;
        public boolean isSuccess;
        public List<Case> cases;
    }
    public static class Case
    {
        public long id;
        public String name;
        public boolean isSuccess;
        public List<CaseIteration> iterations;
    }
    public static class CaseIteration
    {
        public int iterationNum;
        public boolean isSuccess;
        public boolean hasWarnings;
        public String context;
        public String failure;
        public Map<String, String> har;
        public List<Step> steps;
    }
    public static class Step
    {
        public String name;
        public short order;
        public String failure;
        public boolean isSuccess;
        public String transactionName;
        public String screenshot;
        public Integer domContentLoadedEvent;
        public Integer loadEvent;
        public double duration;
        public int iterationNum;
    }
}
