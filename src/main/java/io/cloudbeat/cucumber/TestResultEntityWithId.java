package io.cloudbeat.cucumber;

public abstract class TestResultEntityWithId extends TestResultBase {
    public long id;
    public FailureModel failure;
}
