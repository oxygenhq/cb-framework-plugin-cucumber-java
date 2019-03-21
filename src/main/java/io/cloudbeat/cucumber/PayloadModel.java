package io.cloudbeat.cucumber;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
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

    public static PayloadModel Load(String path) throws IOException {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        String json = new String(encoded, StandardCharsets.UTF_8);

        PayloadModel payload = new PayloadModel();

        final ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readValue(json, JsonNode.class);
        TypeReference<Map<String, Object>> mapTypeRef = new TypeReference<Map<String, Object>>() {};

        payload.runId = rootNode.get("RunId").textValue();
        payload.instanceId = rootNode.get("InstanceId").textValue();
        payload.capabilities = mapper.readValue(rootNode.get("Capabilities").toString(), mapTypeRef);
        payload.metadata = mapper.readValue(rootNode.get("Metadata").toString(), mapTypeRef);
        payload.environmentVariables = rootNode.get("EnvironmentVariables").textValue();

        for (JsonNode caseNode : rootNode.get("Cases")) {
            PayloadModel.Case caze = new PayloadModel.Case();
            caze.id = caseNode.get("Id").asLong();
            JsonNode details =  caseNode.get("Details");
            caze.cucumberId = details.get("cucumberId").textValue();
            caze.order = caseNode.get("Order").asInt();
            payload.cases.put(caze.cucumberId, caze);
        }

        return payload;
    }
}
