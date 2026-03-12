package com.synapxnet.aiopsk8sservice.service.impl;

import com.synapxnet.aiopsk8sservice.exception.K8sResourceNotFoundException;
import com.synapxnet.aiopsk8sservice.service.K8sClientFactory;
import com.synapxnet.aiopsk8sservice.service.K8sJobService;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.batch.v1.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class K8sJobServiceImpl implements K8sJobService {

    private static final Logger log = LoggerFactory.getLogger(K8sJobServiceImpl.class);

    private final K8sClientFactory clientFactory;

    public K8sJobServiceImpl(K8sClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    // ====== Jobs ======

    @Override
    public List<Map<String, Object>> listJobs(Long clusterId, String namespace) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        List<Job> jobs;
        if (namespace == null || namespace.isEmpty()) {
            jobs = client.batch().v1().jobs().inAnyNamespace().list().getItems();
        } else {
            jobs = client.batch().v1().jobs().inNamespace(namespace).list().getItems();
        }
        return jobs.stream().map(this::jobToMap).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getJob(Long clusterId, String namespace, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        Job job = client.batch().v1().jobs().inNamespace(namespace).withName(name).get();
        if (job == null) {
            throw new K8sResourceNotFoundException("Job not found: " + namespace + "/" + name);
        }
        Map<String, Object> map = jobToMap(job);

        // Add containers info
        if (job.getSpec().getTemplate().getSpec().getContainers() != null) {
            List<Map<String, Object>> containers = job.getSpec().getTemplate().getSpec().getContainers().stream()
                    .map(this::containerToMap).collect(Collectors.toList());
            map.put("containers", containers);
        }

        map.put("labels", job.getMetadata().getLabels());
        map.put("annotations", job.getMetadata().getAnnotations());
        map.put("backoffLimit", job.getSpec().getBackoffLimit());
        map.put("completions", job.getSpec().getCompletions());
        map.put("parallelism", job.getSpec().getParallelism());
        map.put("activeDeadlineSeconds", job.getSpec().getActiveDeadlineSeconds());
        map.put("yaml", Serialization.asYaml(job));

        // Get associated pods
        Map<String, String> selector = new HashMap<>();
        if (job.getSpec().getSelector() != null && job.getSpec().getSelector().getMatchLabels() != null) {
            selector = job.getSpec().getSelector().getMatchLabels();
        }
        if (!selector.isEmpty()) {
            List<Pod> pods = client.pods().inNamespace(namespace).withLabels(selector).list().getItems();
            List<Map<String, Object>> podList = pods.stream().map(pod -> {
                Map<String, Object> podMap = new HashMap<>();
                podMap.put("name", pod.getMetadata().getName());
                podMap.put("status", pod.getStatus().getPhase());
                podMap.put("createdAt", pod.getMetadata().getCreationTimestamp());
                return podMap;
            }).collect(Collectors.toList());
            map.put("pods", podList);
        }

        return map;
    }

    @Override
    public void createJob(Long clusterId, String namespace, String yaml) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        Job job = Serialization.unmarshal(yaml, Job.class);
        if (job.getMetadata().getNamespace() == null) {
            job.getMetadata().setNamespace(namespace);
        }
        client.batch().v1().jobs().inNamespace(namespace).resource(job).create();
    }

    @Override
    public void deleteJob(Long clusterId, String namespace, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        // Delete job and its pods
        client.batch().v1().jobs().inNamespace(namespace).withName(name)
                .withPropagationPolicy(DeletionPropagation.BACKGROUND).delete();
    }

    @Override
    public void rerunJob(Long clusterId, String namespace, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        Job oldJob = client.batch().v1().jobs().inNamespace(namespace).withName(name).get();
        if (oldJob == null) {
            throw new K8sResourceNotFoundException("Job not found: " + namespace + "/" + name);
        }

        // Delete old job
        client.batch().v1().jobs().inNamespace(namespace).withName(name)
                .withPropagationPolicy(DeletionPropagation.BACKGROUND).delete();

        // Create new job with same spec
        Job newJob = new JobBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .withLabels(oldJob.getMetadata().getLabels())
                    .withAnnotations(oldJob.getMetadata().getAnnotations())
                .endMetadata()
                .withSpec(oldJob.getSpec())
                .build();
        // Clear selector and template labels that were auto-generated
        newJob.getSpec().setSelector(null);
        if (newJob.getSpec().getTemplate().getMetadata() != null) {
            newJob.getSpec().getTemplate().getMetadata().setLabels(null);
        }

        client.batch().v1().jobs().inNamespace(namespace).resource(newJob).create();
    }

    // ====== CronJobs ======

    @Override
    public List<Map<String, Object>> listCronJobs(Long clusterId, String namespace) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        List<CronJob> cronJobs;
        if (namespace == null || namespace.isEmpty()) {
            cronJobs = client.batch().v1().cronjobs().inAnyNamespace().list().getItems();
        } else {
            cronJobs = client.batch().v1().cronjobs().inNamespace(namespace).list().getItems();
        }
        return cronJobs.stream().map(this::cronJobToMap).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getCronJob(Long clusterId, String namespace, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        CronJob cronJob = client.batch().v1().cronjobs().inNamespace(namespace).withName(name).get();
        if (cronJob == null) {
            throw new K8sResourceNotFoundException("CronJob not found: " + namespace + "/" + name);
        }
        Map<String, Object> map = cronJobToMap(cronJob);

        // Add detail info
        if (cronJob.getSpec().getJobTemplate().getSpec().getTemplate().getSpec().getContainers() != null) {
            List<Map<String, Object>> containers = cronJob.getSpec().getJobTemplate().getSpec().getTemplate().getSpec().getContainers().stream()
                    .map(this::containerToMap).collect(Collectors.toList());
            map.put("containers", containers);
        }

        map.put("labels", cronJob.getMetadata().getLabels());
        map.put("annotations", cronJob.getMetadata().getAnnotations());
        map.put("concurrencyPolicy", cronJob.getSpec().getConcurrencyPolicy());
        map.put("successfulJobsHistoryLimit", cronJob.getSpec().getSuccessfulJobsHistoryLimit());
        map.put("failedJobsHistoryLimit", cronJob.getSpec().getFailedJobsHistoryLimit());
        map.put("startingDeadlineSeconds", cronJob.getSpec().getStartingDeadlineSeconds());
        map.put("yaml", Serialization.asYaml(cronJob));

        // List recent jobs created by this cronjob
        List<Job> jobs = client.batch().v1().jobs().inNamespace(namespace).list().getItems().stream()
                .filter(j -> {
                    if (j.getMetadata().getOwnerReferences() == null) return false;
                    return j.getMetadata().getOwnerReferences().stream()
                            .anyMatch(ref -> "CronJob".equals(ref.getKind()) && name.equals(ref.getName()));
                })
                .sorted((a, b) -> {
                    String ta = a.getMetadata().getCreationTimestamp();
                    String tb = b.getMetadata().getCreationTimestamp();
                    if (ta == null || tb == null) return 0;
                    return tb.compareTo(ta);
                })
                .limit(10)
                .collect(Collectors.toList());

        List<Map<String, Object>> jobList = jobs.stream().map(this::jobToMap).collect(Collectors.toList());
        map.put("recentJobs", jobList);

        return map;
    }

    @Override
    public void createCronJob(Long clusterId, String namespace, String yaml) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        CronJob cronJob = Serialization.unmarshal(yaml, CronJob.class);
        if (cronJob.getMetadata().getNamespace() == null) {
            cronJob.getMetadata().setNamespace(namespace);
        }
        client.batch().v1().cronjobs().inNamespace(namespace).resource(cronJob).create();
    }

    @Override
    public void updateCronJob(Long clusterId, String namespace, String name, String yaml) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        CronJob cronJob = Serialization.unmarshal(yaml, CronJob.class);
        cronJob.getMetadata().setName(name);
        cronJob.getMetadata().setNamespace(namespace);
        client.batch().v1().cronjobs().inNamespace(namespace).resource(cronJob).update();
    }

    @Override
    public void deleteCronJob(Long clusterId, String namespace, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        client.batch().v1().cronjobs().inNamespace(namespace).withName(name).delete();
    }

    @Override
    public void triggerCronJob(Long clusterId, String namespace, String name) {
        KubernetesClient client = clientFactory.getClient(clusterId);
        CronJob cronJob = client.batch().v1().cronjobs().inNamespace(namespace).withName(name).get();
        if (cronJob == null) {
            throw new K8sResourceNotFoundException("CronJob not found: " + namespace + "/" + name);
        }

        // Create a Job from the CronJob's template
        String jobName = name + "-manual-" + System.currentTimeMillis() / 1000;
        Job job = new JobBuilder()
                .withNewMetadata()
                    .withName(jobName)
                    .withNamespace(namespace)
                    .addToAnnotations("cronjob.kubernetes.io/instantiate", "manual")
                .endMetadata()
                .withSpec(cronJob.getSpec().getJobTemplate().getSpec())
                .build();

        client.batch().v1().jobs().inNamespace(namespace).resource(job).create();
    }

    // ====== Helpers ======

    private Map<String, Object> jobToMap(Job job) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", job.getMetadata().getName());
        map.put("namespace", job.getMetadata().getNamespace());
        map.put("createdAt", job.getMetadata().getCreationTimestamp());
        map.put("kind", "Job");

        JobStatus status = job.getStatus();
        if (status != null) {
            map.put("active", status.getActive() != null ? status.getActive() : 0);
            map.put("succeeded", status.getSucceeded() != null ? status.getSucceeded() : 0);
            map.put("failed", status.getFailed() != null ? status.getFailed() : 0);
            map.put("startTime", status.getStartTime());
            map.put("completionTime", status.getCompletionTime());

            // Determine status string
            if (status.getCompletionTime() != null) {
                map.put("status", "Completed");
            } else if (status.getActive() != null && status.getActive() > 0) {
                map.put("status", "Running");
            } else if (status.getFailed() != null && status.getFailed() > 0) {
                map.put("status", "Failed");
            } else {
                map.put("status", "Pending");
            }
        } else {
            map.put("status", "Unknown");
        }

        // Duration
        if (status != null && status.getStartTime() != null && status.getCompletionTime() != null) {
            map.put("duration", status.getCompletionTime());
        }

        // Images
        if (job.getSpec().getTemplate().getSpec().getContainers() != null) {
            List<String> images = job.getSpec().getTemplate().getSpec().getContainers().stream()
                    .map(Container::getImage).collect(Collectors.toList());
            map.put("images", images);
        }

        return map;
    }

    private Map<String, Object> cronJobToMap(CronJob cronJob) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", cronJob.getMetadata().getName());
        map.put("namespace", cronJob.getMetadata().getNamespace());
        map.put("createdAt", cronJob.getMetadata().getCreationTimestamp());
        map.put("kind", "CronJob");
        map.put("schedule", cronJob.getSpec().getSchedule());
        map.put("suspend", Boolean.TRUE.equals(cronJob.getSpec().getSuspend()));

        CronJobStatus status = cronJob.getStatus();
        if (status != null) {
            map.put("lastScheduleTime", status.getLastScheduleTime());
            map.put("lastSuccessfulTime", status.getLastSuccessfulTime());
            if (status.getActive() != null) {
                map.put("activeJobs", status.getActive().size());
            } else {
                map.put("activeJobs", 0);
            }
        }

        map.put("status", Boolean.TRUE.equals(cronJob.getSpec().getSuspend()) ? "Suspended" : "Active");

        // Images
        if (cronJob.getSpec().getJobTemplate().getSpec().getTemplate().getSpec().getContainers() != null) {
            List<String> images = cronJob.getSpec().getJobTemplate().getSpec().getTemplate().getSpec().getContainers().stream()
                    .map(Container::getImage).collect(Collectors.toList());
            map.put("images", images);
        }

        return map;
    }

    private Map<String, Object> containerToMap(Container container) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", container.getName());
        map.put("image", container.getImage());
        map.put("command", container.getCommand());
        map.put("args", container.getArgs());

        if (container.getResources() != null) {
            Map<String, Object> resources = new HashMap<>();
            if (container.getResources().getRequests() != null) {
                Map<String, String> requests = new HashMap<>();
                container.getResources().getRequests().forEach((k, v) -> requests.put(k, v.getAmount()));
                resources.put("requests", requests);
            }
            if (container.getResources().getLimits() != null) {
                Map<String, String> limits = new HashMap<>();
                container.getResources().getLimits().forEach((k, v) -> limits.put(k, v.getAmount()));
                resources.put("limits", limits);
            }
            map.put("resources", resources);
        }

        return map;
    }
}
