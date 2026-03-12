package com.synapxnet.aiopsk8sservice.controller;

import com.synapxnet.aiopsk8sservice.common.Result;
import com.synapxnet.aiopsk8sservice.entity.K8sCluster;
import com.synapxnet.aiopsk8sservice.entity.K8sClusterComponent;
import com.synapxnet.aiopsk8sservice.entity.K8sClusterMetricsSnapshot;
import com.synapxnet.aiopsk8sservice.service.K8sClusterService;
import com.synapxnet.aiopsk8sservice.service.K8sMetricsService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/k8s/clusters")
public class K8sClusterController {

    private final K8sClusterService clusterService;
    private final K8sMetricsService metricsService;

    public K8sClusterController(K8sClusterService clusterService, K8sMetricsService metricsService) {
        this.clusterService = clusterService;
        this.metricsService = metricsService;
    }

    @GetMapping
    public Result<List<K8sCluster>> listClusters() {
        return Result.success(clusterService.listAll());
    }

    @GetMapping("/{id}")
    public Result<K8sCluster> getCluster(@PathVariable Long id) {
        return Result.success(clusterService.getById(id));
    }

    @PostMapping
    public Result<K8sCluster> createCluster(@RequestBody Map<String, Object> body) {
        K8sCluster cluster = new K8sCluster();
        cluster.setName((String) body.get("name"));
        cluster.setDescription((String) body.get("description"));
        cluster.setProvider((String) body.get("provider"));
        populateSshFields(cluster, body);
        String kubeconfig = (String) body.get("kubeconfig");
        return Result.success(clusterService.create(cluster, kubeconfig));
    }

    @PutMapping("/{id}")
    public Result<K8sCluster> updateCluster(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        K8sCluster cluster = new K8sCluster();
        cluster.setName((String) body.get("name"));
        cluster.setDescription((String) body.get("description"));
        cluster.setProvider((String) body.get("provider"));
        populateSshFields(cluster, body);
        String kubeconfig = (String) body.get("kubeconfig");
        return Result.success(clusterService.update(id, cluster, kubeconfig));
    }

    private void populateSshFields(K8sCluster cluster, Map<String, Object> body) {
        cluster.setSshHost((String) body.get("sshHost"));
        Object portObj = body.get("sshPort");
        if (portObj instanceof Number) {
            cluster.setSshPort(((Number) portObj).intValue());
        } else if (portObj instanceof String) {
            try { cluster.setSshPort(Integer.parseInt((String) portObj)); } catch (NumberFormatException ignored) {}
        }
        cluster.setSshUser((String) body.get("sshUser"));
        cluster.setSshPassword((String) body.get("sshPassword"));
        cluster.setSshKey((String) body.get("sshKey"));
    }

    @PutMapping("/{id}/ssh")
    public Result<Void> updateSsh(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        K8sCluster cluster = clusterService.getById(id);
        cluster.setSshHost((String) body.get("sshHost"));
        Object portObj = body.get("sshPort");
        if (portObj instanceof Number) {
            cluster.setSshPort(((Number) portObj).intValue());
        } else if (portObj instanceof String) {
            try { cluster.setSshPort(Integer.parseInt((String) portObj)); } catch (NumberFormatException ignored) {}
        }
        cluster.setSshUser((String) body.get("sshUser"));
        cluster.setSshPassword((String) body.get("sshPassword"));
        cluster.setSshKey((String) body.get("sshKey"));
        clusterService.updateSsh(cluster);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteCluster(@PathVariable Long id) {
        clusterService.delete(id);
        return Result.success();
    }

    @PostMapping("/test-connection")
    public Result<Map<String, Object>> testConnection(@RequestBody Map<String, String> body) {
        String kubeconfig = body.get("kubeconfig");
        return Result.success(clusterService.testConnection(kubeconfig));
    }

    @GetMapping("/{id}/overview")
    public Result<Map<String, Object>> getOverview(@PathVariable Long id) {
        return Result.success(clusterService.getOverview(id));
    }

    @GetMapping("/{id}/components")
    public Result<List<K8sClusterComponent>> getComponents(@PathVariable Long id) {
        return Result.success(clusterService.getComponents(id));
    }

    @GetMapping("/{id}/metrics")
    public Result<K8sClusterMetricsSnapshot> getMetrics(@PathVariable Long id) {
        return Result.success(clusterService.getMetrics(id));
    }

    @GetMapping("/{id}/events")
    public Result<List<Map<String, Object>>> getEvents(
            @PathVariable Long id,
            @RequestParam(defaultValue = "50") int limit) {
        return Result.success(clusterService.getEvents(id, limit));
    }

    @GetMapping("/{id}/kubeconfig")
    public Result<String> getKubeconfig(@PathVariable Long id) {
        return Result.success(clusterService.getKubeconfig(id));
    }

    @GetMapping("/{id}/node-ranking")
    public Result<List<Map<String, Object>>> getNodeRanking(
            @PathVariable Long id,
            @RequestParam(defaultValue = "cpu") String sortBy,
            @RequestParam(defaultValue = "5") int limit) {
        return Result.success(metricsService.getNodeRanking(id, sortBy, limit));
    }
}
