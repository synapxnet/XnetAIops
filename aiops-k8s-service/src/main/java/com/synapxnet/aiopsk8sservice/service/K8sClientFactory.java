package com.synapxnet.aiopsk8sservice.service;

import com.synapxnet.aiopsk8sservice.entity.K8sCluster;
import com.synapxnet.aiopsk8sservice.exception.ClusterConnectionException;
import com.synapxnet.aiopsk8sservice.mapper.K8sClusterMapper;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class K8sClientFactory {

    private static final Logger log = LoggerFactory.getLogger(K8sClientFactory.class);

    private final K8sClusterMapper clusterMapper;
    private final Map<Long, KubernetesClient> clientCache = new ConcurrentHashMap<>();

    @Value("${k8s.encryption.key}")
    private String encryptionKey;

    public K8sClientFactory(K8sClusterMapper clusterMapper) {
        this.clusterMapper = clusterMapper;
    }

    public KubernetesClient getClient(Long clusterId) {
        return clientCache.computeIfAbsent(clusterId, id -> {
            K8sCluster cluster = clusterMapper.findById(id);
            if (cluster == null) {
                throw new ClusterConnectionException("Cluster not found: " + id);
            }
            return createClient(cluster);
        });
    }

    public KubernetesClient createClientFromKubeconfig(String kubeconfigContent) {
        try {
            Config config = Config.fromKubeconfig(null, kubeconfigContent, null);
            config.setConnectionTimeout(10000);
            config.setRequestTimeout(30000);
            config.setTrustCerts(true);
            return new KubernetesClientBuilder().withConfig(config).build();
        } catch (Exception e) {
            throw new ClusterConnectionException("Failed to create K8s client from kubeconfig", e);
        }
    }

    private KubernetesClient createClient(K8sCluster cluster) {
        try {
            String kubeconfig = decryptKubeconfig(cluster.getKubeconfigContent());
            Config config = Config.fromKubeconfig(null, kubeconfig, null);
            config.setConnectionTimeout(10000);
            config.setRequestTimeout(30000);
            config.setTrustCerts(true);
            return new KubernetesClientBuilder().withConfig(config).build();
        } catch (ClusterConnectionException e) {
            throw e;
        } catch (Exception e) {
            throw new ClusterConnectionException("Failed to connect to cluster: " + cluster.getName(), e);
        }
    }

    public void removeClient(Long clusterId) {
        KubernetesClient client = clientCache.remove(clusterId);
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("Error closing K8s client for cluster {}: {}", clusterId, e.getMessage());
            }
        }
    }

    public void refreshClient(Long clusterId) {
        removeClient(clusterId);
        getClient(clusterId);
    }

    public String encryptKubeconfig(String plainKubeconfig) {
        try {
            byte[] keyBytes = padKey(encryptionKey);
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(plainKubeconfig.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt kubeconfig", e);
        }
    }

    public String decryptKubeconfig(String encryptedKubeconfig) {
        try {
            byte[] keyBytes = padKey(encryptionKey);
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedKubeconfig));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt kubeconfig", e);
        }
    }

    private byte[] padKey(String key) {
        byte[] keyBytes = new byte[16];
        byte[] original = key.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(original, 0, keyBytes, 0, Math.min(original.length, 16));
        return keyBytes;
    }
}
