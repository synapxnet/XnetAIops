package com.synapxnet.aiopsclmservice.service.impl;

import com.synapxnet.aiopsclmservice.entity.JenkinsVersion;
import com.synapxnet.aiopsclmservice.mapper.JenkinsVersionMapper;
import com.synapxnet.aiopsclmservice.service.JenkinsVersionService;
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
 * Jenkins 版本服务实现
 * 从 mirrors.jenkins.io 获取版本列表
 */
@Service
public class JenkinsVersionServiceImpl implements JenkinsVersionService {

    private static final Logger logger = LoggerFactory.getLogger(JenkinsVersionServiceImpl.class);

    private static final String[] MIRROR_URLS = {
            "https://mirrors.jenkins.io/war-stable/",
            "https://mirrors.tuna.tsinghua.edu.cn/jenkins/war-stable/",
            "https://mirrors.huaweicloud.com/jenkins/war-stable/"
    };

    private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+\\.\\d+(?:\\.\\d+)?)/?$");
    private static final Pattern LTS_PATTERN = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");

    @Autowired
    private JenkinsVersionMapper jenkinsVersionMapper;

    @Override
    public List<JenkinsVersion> getStableVersions() {
        List<JenkinsVersion> versions = jenkinsVersionMapper.findByVersionType("stable");
        if (versions == null || versions.isEmpty()) {
            logger.info("数据库中无版本数据，尝试从镜像站获取...");
            refreshVersions();
            versions = jenkinsVersionMapper.findByVersionType("stable");
        }
        return versions != null ? versions : Collections.emptyList();
    }

    @Override
    public List<JenkinsVersion> getLtsVersions() {
        List<JenkinsVersion> versions = jenkinsVersionMapper.findLtsVersions();
        if (versions == null || versions.isEmpty()) {
            logger.info("数据库中无LTS版本数据，尝试从镜像站获取...");
            refreshVersions();
            versions = jenkinsVersionMapper.findLtsVersions();
        }
        return versions != null ? versions : Collections.emptyList();
    }

    @Override
    @Transactional
    public Map<String, Object> refreshVersions() {
        Map<String, Object> result = new HashMap<>();
        long startTime = System.currentTimeMillis();
        int totalCount = 0;

        try {
            logger.info("开始从镜像站刷新 Jenkins 版本列表...");

            List<JenkinsVersion> versions = null;
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

            jenkinsVersionMapper.resetLatestFlag("stable");

            if (!versions.isEmpty()) {
                versions.get(0).setIsLatest(true);
            }

            for (JenkinsVersion version : versions) {
                JenkinsVersion existing = jenkinsVersionMapper.findByVersionAndType(
                        version.getVersion(), version.getVersionType());
                if (existing != null) {
                    version.setId(existing.getId());
                    jenkinsVersionMapper.update(version);
                } else {
                    jenkinsVersionMapper.insert(version);
                }
                totalCount++;
            }

            result.put("success", true);
            result.put("count", totalCount);
            result.put("mirror", usedMirror);
            result.put("message", "成功同步 " + totalCount + " 个版本");

        } catch (Exception e) {
            logger.error("刷新版本列表失败: {}", e.getMessage(), e);
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
        stats.put("stableCount", jenkinsVersionMapper.countByVersionType("stable"));
        stats.put("weeklyCount", jenkinsVersionMapper.countByVersionType("weekly"));

        List<JenkinsVersion> ltsVersions = jenkinsVersionMapper.findLtsVersions();
        stats.put("ltsCount", ltsVersions != null ? ltsVersions.size() : 0);

        List<JenkinsVersion> stableVersions = jenkinsVersionMapper.findByVersionType("stable");
        if (stableVersions != null && !stableVersions.isEmpty()) {
            stats.put("latestVersion", stableVersions.get(0).getVersion());
        }

        return stats;
    }

    private List<JenkinsVersion> fetchVersionsFromMirror(String mirrorUrl) throws Exception {
        List<JenkinsVersion> versions = new ArrayList<>();

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

                JenkinsVersion version = new JenkinsVersion();
                version.setVersion(versionStr);
                version.setVersionType("stable");
                version.setDownloadUrl(mirrorUrl + versionStr + "/jenkins.war");
                version.setIsLts(LTS_PATTERN.matcher(versionStr).matches());
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
