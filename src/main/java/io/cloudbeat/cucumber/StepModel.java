package io.cloudbeat.cucumber;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.Dictionary;


@JsonIgnoreProperties(value = { "isFinished" })
public class StepModel extends TestResultBase {
    public String location;
    public Dictionary<String, String> stats;
    public FailureModel failure;
    public String screenShot;
    public ArrayList<StepModel> steps;
    public boolean isFinished;
    public int order;
    public String transaction;
}
