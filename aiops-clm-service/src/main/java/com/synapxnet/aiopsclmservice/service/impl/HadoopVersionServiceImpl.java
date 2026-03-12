package com.synapxnet.aiopsclmservice.service.impl;

import com.synapxnet.aiopsclmservice.entity.HadoopVersion;
import com.synapxnet.aiopsclmservice.mapper.HadoopVersionMapper;
import com.synapxnet.aiopsclmservice.service.HadoopVersionService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hadoop 版本服务实现
 * 从 Apache Hadoop 官网获取版本列表
 */
@Service
public class HadoopVersionServiceImpl implements HadoopVersionService {

    private static final Logger logger = LoggerFactory.getLogger(HadoopVersionServiceImpl.class);

    private static final String HADOOP_ARCHIVE_URL = "https://archive.apache.org/dist/hadoop/common/";

    private static final String[] MIRROR_URLS = {
            "https://archive.apache.org/dist/hadoop/common/",
            "https://mirrors.tuna.tsinghua.edu.cn/apache/hadoop/common/",
            "https://mirrors.huaweicloud.com/apache/hadoop/common/"
    };

    private static final Pattern VERSION_PATTERN = Pattern.compile("^hadoop-(\\d+\\.\\d+\\.\\d+)/?$");

    @Autowired
    private HadoopVersionMapper hadoopVersionMapper;

    @Override
    public List<HadoopVersion> getAllVersions() {
        List<HadoopVersion> versions = hadoopVersionMapper.findAll();
        if (versions == null || versions.isEmpty()) {
            logger.info("数据库中无版本数据，尝试从官网获取...");
            refreshVersions();
            versions = hadoopVersionMapper.findAll();
        }
        return versions != null ? versions : Collections.emptyList();
    }

    @Override
    public List<HadoopVersion> getStableVersions() {
        List<HadoopVersion> versions = hadoopVersionMapper.findStableVersions();
        if (versions == null || versions.isEmpty()) {
            logger.info("数据库中无稳定版本数据，尝试从官网获取...");
            refreshVersions();
            versions = hadoopVersionMapper.findStableVersions();
        }
        return versions != null ? versions : Collections.emptyList();
    }

    @Override
    @Transactional
    public Map<String, Object> refreshVersions() {
        Map<String, Object> result = new HashMap<>();
        long startTime = System.currentTimeMillis();
        int totalCount = 0;
        String errorMessage = null;

        try {
            logger.info("开始从 Apache 官网刷新 Hadoop 版本列表...");

            List<HadoopVersion> versions = null;
            String usedMirror = null;

            for (String mirrorUrl : MIRROR_URLS) {
                try {
                    logger.info("尝试镜像源: {}", mirrorUrl);
                    versions = fetchVersionsFromMirror(mirrorUrl);
                    if (versions != null && !versions.isEmpty()) {
                        usedMirror = mirrorUrl;
                        break;
                    }
                } catch (Exception e) {
                    logger.warn("镜像源 {} 获取失败: {}", mirrorUrl, e.getMessage());
                }
            }

            if (versions == null || versions.isEmpty()) {
                throw new RuntimeException("所有镜像源都无法获取版本列表");
            }

            logger.info("从 {} 获取到 {} 个版本", usedMirror, versions.size());

            hadoopVersionMapper.resetLatestFlag("stable");

            if (!versions.isEmpty()) {
                versions.get(0).setIsLatest(true);
            }

            for (HadoopVersion version : versions) {
                HadoopVersion existing = hadoopVersionMapper.findByVersion(version.getVersion());
                if (existing != null) {
                    version.setId(existing.getId());
                    hadoopVersionMapper.update(version);
                } else {
                    hadoopVersionMapper.insert(version);
                }
                totalCount++;
            }

            result.put("success", true);
            result.put("count", totalCount);
            result.put("mirror", usedMirror);
            result.put("message", "成功同步 " + totalCount + " 个版本");

        } catch (Exception e) {
            logger.error("刷新版本列表失败: {}", e.getMessage(), e);
            errorMessage = e.getMessage();
            result.put("success", false);
            result.put("message", "刷新失败: " + e.getMessage());
        }

        long duration = System.currentTimeMillis() - startTime;
        result.put("durationMs", duration);

        return result;
    }

    @Override
    public Map<String, Object> getVersionStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("stableCount", hadoopVersionMapper.countByVersionType("stable"));

        List<HadoopVersion> versions = hadoopVersionMapper.findStableVersions();
        if (versions != null && !versions.isEmpty()) {
            stats.put("latestVersion", versions.get(0).getVersion());
        }

        return stats;
    }

    private List<HadoopVersion> fetchVersionsFromMirror(String mirrorUrl) throws Exception {
        List<HadoopVersion> versions = new ArrayList<>();

        Document doc = Jsoup.connect(mirrorUrl)
                .timeout(30000)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .get();

        Elements links = doc.select("a[href]");

        for (Element link : links) {
            String href = link.attr("href");
            Matcher matcher = VERSION_PATTERN.matcher(href);

            if (matcher.matches()) {
                String versionStr = matcher.group(1);

                if (!versionStr.startsWith("3.")) {
                    continue;
                }

                HadoopVersion version = new HadoopVersion();
                version.setVersion(versionStr);
                version.setVersionType("stable");
                version.setDownloadUrl(mirrorUrl + "hadoop-" + versionStr + "/hadoop-" + versionStr + ".tar.gz");
                version.setIsLatest(false);

                try {
                    Element parent = link.parent();
                    if (parent != null) {
                        String text = parent.text();
                        Pattern datePattern = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");
                        Matcher dateMatcher = datePattern.matcher(text);
                        if (dateMatcher.find()) {
                            version.setReleaseDate(LocalDate.parse(dateMatcher.group(1)));
                        }
                    }
                } catch (Exception e) {
                    // ignore date parse failure
                }

                versions.add(version);
            }
        }

        versions.sort((v1, v2) -> compareVersions(v2.getVersion(), v1.getVersion()));
        return versions;
    }

    private int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        int maxLength = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < maxLength; i++) {
            int num1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int num2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
            if (num1 != num2) {
                return Integer.compare(num1, num2);
            }
        }
        return 0;
    }
}
