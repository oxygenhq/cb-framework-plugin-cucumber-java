package io.cloudbeat.cucumber;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class StatusModel {
    public enum Statuses
    {
        Pending(0),
        Initializing(1),
        Running(2),
        Finished(3),
        Canceling(4),
        Canceled(5);

        private final int value;

        Statuses(final int newValue) {
            value = newValue;
        }

        public int getValue() { return value; }
    }

    public String runId;
    public String instanceId;
    public int status;
    public float progress;
    @JsonProperty("case")
    public CaseStatus caze;

    public static class CaseStatus {
        public long id;
        public String name;
        public int order;
        public int iterationsPassed;
        public int iterationsFailed;
        public float progress;
        public List<Failure> failures;

        // FIXME: this should be updated on the backend
        public class Failure {
            public String message;
            public String type;
            public String subtype;
            public int line;
            public String details;
            public boolean isFatal;
        }
    }
}
