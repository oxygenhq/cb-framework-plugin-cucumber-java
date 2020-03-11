package io.cloudbeat.cucumber;

import java.util.ArrayList;
import java.util.Dictionary;

public class CaseModel extends TestResultEntityWithId {
    public int iterationNum;
    public Dictionary<String, Object> сontext;
    public ArrayList<StepModel> steps;
    public short order;
}
