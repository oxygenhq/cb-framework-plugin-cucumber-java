package io.cloudbeat.cucumber;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PayloadModel {
    public String runId;
    public String instanceId;
    public Map<String, String> metadata;
    public Map<String, String> capabilities;
    public String environmentVariables;

    public Map<String, Case> cases = new HashMap<>();

    public static class Case {
        public long id;
        public String cucumberId;
        public int order;
    }
}
