package com.synapxnet.aiopsk8sservice.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class JenkinsClient {

    private static final Logger log = LoggerFactory.getLogger(JenkinsClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String baseUrl;
    private final String username;
    private final String apiToken;
    private final RestTemplate restTemplate;

    public JenkinsClient(String baseUrl, String username, String apiToken) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.username = username;
        this.apiToken = apiToken;
        this.restTemplate = new RestTemplate();
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String auth = username + ":" + apiToken;
        String encoded = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + encoded);
        return headers;
    }

    /** 获取 crumb + session cookie（Jenkins CSRF 要求两者配对） */
    private String[] fetchCrumbAndCookie() {
        try {
            HttpHeaders headers = createHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> resp = restTemplate.exchange(
                    baseUrl + "/crumbIssuer/api/json", HttpMethod.GET, entity, Map.class);
            String crumb = null;
            String crumbField = "Jenkins-Crumb";
            if (resp.getBody() != null) {
                crumb = (String) resp.getBody().get("crumb");
                if (resp.getBody().get("crumbRequestField") != null) {
                    crumbField = (String) resp.getBody().get("crumbRequestField");
                }
            }
            // 提取 session cookie
            List<String> cookies = resp.getHeaders().get("Set-Cookie");
            String cookie = null;
            if (cookies != null && !cookies.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (String c : cookies) {
                    if (sb.length() > 0) sb.append("; ");
                    // 只取 cookie name=value 部分
                    sb.append(c.split(";")[0]);
                }
                cookie = sb.toString();
            }
            return new String[]{crumb, crumbField, cookie};
        } catch (Exception e) {
            log.debug("No crumb issuer available: {}", e.getMessage());
        }
        return new String[]{null, null, null};
    }

    private HttpHeaders headersWithCrumb() {
        HttpHeaders headers = createHeaders();
        String[] crumbInfo = fetchCrumbAndCookie();
        if (crumbInfo[0] != null) {
            headers.set(crumbInfo[1] != null ? crumbInfo[1] : "Jenkins-Crumb", crumbInfo[0]);
        }
        if (crumbInfo[2] != null) {
            headers.set("Cookie", crumbInfo[2]);
        }
        return headers;
    }

    // ====== Connection Test ======

    public boolean testConnection() {
        try {
            HttpEntity<String> entity = new HttpEntity<>(createHeaders());
            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl + "/api/json", HttpMethod.GET, entity, String.class);
            return resp.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Jenkins connection test failed: {}", e.getMessage());
            return false;
        }
    }

    // ====== Job CRUD ======

    public void createPipelineJob(String jobName, String jenkinsfile, String description,
                                   boolean disableConcurrent, String timerTrigger) {
        String xml = buildPipelineJobXml(jenkinsfile, description, disableConcurrent, timerTrigger);
        HttpHeaders headers = headersWithCrumb();
        headers.setContentType(MediaType.APPLICATION_XML);
        HttpEntity<String> entity = new HttpEntity<>(xml, headers);
        restTemplate.exchange(
                baseUrl + "/createItem?name=" + jobName, HttpMethod.POST, entity, String.class);
    }

    public void updatePipelineJob(String jobName, String jenkinsfile, String description,
                                   boolean disableConcurrent, String timerTrigger) {
        String xml = buildPipelineJobXml(jenkinsfile, description, disableConcurrent, timerTrigger);
        HttpHeaders headers = headersWithCrumb();
        headers.setContentType(MediaType.APPLICATION_XML);
        HttpEntity<String> entity = new HttpEntity<>(xml, headers);
        restTemplate.exchange(
                baseUrl + "/job/" + jobName + "/config.xml", HttpMethod.POST, entity, String.class);
    }

    public void deleteJob(String jobName) {
        HttpHeaders headers = headersWithCrumb();
        HttpEntity<String> entity = new HttpEntity<>(headers);
        restTemplate.exchange(
                baseUrl + "/job/" + jobName + "/doDelete", HttpMethod.POST, entity, String.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getJobInfo(String jobName) {
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl + "/job/" + jobName + "/api/json", HttpMethod.GET, entity, Map.class);
        return resp.getBody();
    }

    // ====== Build Operations ======

    public int triggerBuild(String jobName, Map<String, String> params) {
        HttpHeaders headers = headersWithCrumb();
        HttpEntity<String> entity = new HttpEntity<>(headers);

        String url;
        if (params != null && !params.isEmpty()) {
            StringBuilder sb = new StringBuilder(baseUrl + "/job/" + jobName + "/buildWithParameters?");
            params.forEach((k, v) -> sb.append(k).append("=").append(v).append("&"));
            url = sb.toString();
        } else {
            url = baseUrl + "/job/" + jobName + "/build";
        }

        ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        // Extract queue item ID from Location header
        String location = resp.getHeaders().getFirst("Location");
        if (location != null && location.contains("queue/item/")) {
            String queueId = location.substring(location.indexOf("queue/item/") + 11).replace("/", "");
            return Integer.parseInt(queueId);
        }
        return -1;
    }

    public int getQueueItemBuildNumber(int queueId) {
        try {
            HttpEntity<String> entity = new HttpEntity<>(createHeaders());
            @SuppressWarnings("unchecked")
            ResponseEntity<Map> resp = restTemplate.exchange(
                    baseUrl + "/queue/item/" + queueId + "/api/json", HttpMethod.GET, entity, Map.class);
            Map<String, Object> body = resp.getBody();
            if (body != null && body.get("executable") != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> executable = (Map<String, Object>) body.get("executable");
                return ((Number) executable.get("number")).intValue();
            }
        } catch (Exception e) {
            log.debug("Queue item {} not ready yet: {}", queueId, e.getMessage());
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getBuildInfo(String jobName, int buildNumber) {
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl + "/job/" + jobName + "/" + buildNumber + "/api/json",
                HttpMethod.GET, entity, Map.class);
        return resp.getBody();
    }

    public String getBuildLog(String jobName, int buildNumber) {
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<String> resp = restTemplate.exchange(
                baseUrl + "/job/" + jobName + "/" + buildNumber + "/consoleText",
                HttpMethod.GET, entity, String.class);
        return resp.getBody();
    }

    public void stopBuild(String jobName, int buildNumber) {
        HttpHeaders headers = headersWithCrumb();
        HttpEntity<String> entity = new HttpEntity<>(headers);
        restTemplate.exchange(
                baseUrl + "/job/" + jobName + "/" + buildNumber + "/stop",
                HttpMethod.POST, entity, String.class);
    }

    // ====== Blue Ocean API (Stage Status) ======

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getBuildStages(String jobName, int buildNumber) {
        try {
            HttpEntity<String> entity = new HttpEntity<>(createHeaders());
            ResponseEntity<List> resp = restTemplate.exchange(
                    baseUrl + "/blue/rest/organizations/jenkins/pipelines/" + jobName +
                            "/runs/" + buildNumber + "/nodes/?limit=10000",
                    HttpMethod.GET, entity, List.class);
            return resp.getBody() != null ? resp.getBody() : Collections.emptyList();
        } catch (Exception e) {
            log.warn("Failed to get build stages via Blue Ocean API: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public String getStageLog(String jobName, int buildNumber, String nodeId) {
        try {
            HttpEntity<String> entity = new HttpEntity<>(createHeaders());
            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl + "/blue/rest/organizations/jenkins/pipelines/" + jobName +
                            "/runs/" + buildNumber + "/nodes/" + nodeId + "/log/",
                    HttpMethod.GET, entity, String.class);
            return resp.getBody();
        } catch (Exception e) {
            log.warn("Failed to get stage log: {}", e.getMessage());
            return "";
        }
    }

    // ====== XML Template ======

    private String buildPipelineJobXml(String jenkinsfile, String description,
                                        boolean disableConcurrent, String timerTrigger) {
        String escapedJenkinsfile = escapeXml(jenkinsfile != null ? jenkinsfile : "");
        String escapedDescription = escapeXml(description != null ? description : "");

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version='1.1' encoding='UTF-8'?>\n");
        xml.append("<flow-definition plugin=\"workflow-job\">\n");
        xml.append("  <description>").append(escapedDescription).append("</description>\n");
        xml.append("  <keepDependencies>false</keepDependencies>\n");
        xml.append("  <properties>\n");
        if (disableConcurrent) {
            xml.append("    <org.jenkinsci.plugins.workflow.job.properties.DisableConcurrentBuildsJobProperty/>\n");
        }
        xml.append("  </properties>\n");
        xml.append("  <definition class=\"org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition\" plugin=\"workflow-cps\">\n");
        xml.append("    <script>").append(escapedJenkinsfile).append("</script>\n");
        xml.append("    <sandbox>true</sandbox>\n");
        xml.append("  </definition>\n");
        xml.append("  <triggers>\n");
        if (timerTrigger != null && !timerTrigger.isBlank()) {
            xml.append("    <hudson.triggers.TimerTrigger>\n");
            xml.append("      <spec>").append(escapeXml(timerTrigger)).append("</spec>\n");
            xml.append("    </hudson.triggers.TimerTrigger>\n");
        }
        xml.append("  </triggers>\n");
        xml.append("  <disabled>false</disabled>\n");
        xml.append("</flow-definition>");
        return xml.toString();
    }

    private String escapeXml(String input) {
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
