package com.synapxnet.aiopsk8sservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
public class K8sDeployScriptGenerator {

    private static final Logger log = LoggerFactory.getLogger(K8sDeployScriptGenerator.class);
    private static final String SCRIPT_BASE_PATH = "scripts/k8s/";

    public String loadTemplate(String scriptName) {
        try {
            ClassPathResource resource = new ClassPathResource(SCRIPT_BASE_PATH + scriptName);
            try (InputStream is = resource.getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load script template: " + scriptName, e);
        }
    }

    public String generate(String template, Map<String, String> variables) {
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    public String generatePrepareNode(String hostname) {
        String template = loadTemplate("prepare-node.sh");
        Map<String, String> vars = new HashMap<>();
        vars.put("HOSTNAME", hostname != null ? hostname : "");
        return generate(template, vars);
    }

    public String generateInstallCri(String criType, String k8sVersion, String registryUrl) {
        if ("cri-o".equals(criType)) {
            String template = loadTemplate("install-crio.sh");
            Map<String, String> vars = new HashMap<>();
            vars.put("K8S_VERSION", k8sVersion);
            return generate(template, vars);
        }
        // Default: containerd
        String template = loadTemplate("install-containerd.sh");
        Map<String, String> vars = new HashMap<>();
        vars.put("REGISTRY_URL", registryUrl != null ? registryUrl : "");
        return generate(template, vars);
    }

    public String generateInstallKubeadm(String k8sVersion) {
        String template = loadTemplate("install-kubeadm.sh");
        Map<String, String> vars = new HashMap<>();
        vars.put("K8S_VERSION", k8sVersion);
        return generate(template, vars);
    }

    public String generateInitMaster(String k8sVersion, String podCidr, String serviceCidr, String masterIp, String registryUrl) {
        String template = loadTemplate("init-master.sh");
        Map<String, String> vars = new HashMap<>();
        vars.put("K8S_VERSION", k8sVersion);
        vars.put("POD_CIDR", podCidr);
        vars.put("SERVICE_CIDR", serviceCidr);
        vars.put("MASTER_IP", masterIp);
        vars.put("REGISTRY_URL", registryUrl != null ? registryUrl : "");
        return generate(template, vars);
    }

    public String generateJoinWorker(String masterIp, String token, String caHash) {
        String template = loadTemplate("join-worker.sh");
        Map<String, String> vars = new HashMap<>();
        vars.put("MASTER_IP", masterIp);
        vars.put("TOKEN", token);
        vars.put("CA_HASH", caHash);
        return generate(template, vars);
    }

    public String generateInstallCni(String cniPlugin, String podCidr, String registryUrl) {
        String template = loadTemplate("install-cni.sh");
        Map<String, String> vars = new HashMap<>();
        vars.put("CNI_PLUGIN", cniPlugin);
        vars.put("POD_CIDR", podCidr);
        vars.put("REGISTRY_URL", registryUrl != null ? registryUrl : "");
        return generate(template, vars);
    }
}
