package top.contins.authservice.util;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URL;
import java.util.Date;

@Slf4j
@Component
public class AliOssUtil {
    @Value("${aliyun.cdnPoint:}")
    private String CDNPoint;

    @Value("${aliyun.oss.endpoint:}")
    private String endPoint;

    @Value("${aliyun.oss.bucketName:}")
    private String bucketName;

    @Value("${aliyun.oss.accessKeyId:}")
    private String accessKeyId;

    @Value("${aliyun.oss.accessKeySecret:}")
    private String accessKeySecret;

    @Value("${aliyun.oss.roleArn:}")
    private String roleArn;

    private OSS ossClient;

    @PostConstruct
    public void init() {
        if (StringUtils.hasText(endPoint) && StringUtils.hasText(accessKeyId) && StringUtils.hasText(accessKeySecret)) {
            try {
                ossClient = new OSSClientBuilder().build(endPoint, accessKeyId, accessKeySecret);
            } catch (Exception e) {
                log.warn("Failed to initialize OSS client: {}", e.getMessage());
            }
        } else {
            log.warn("Aliyun OSS configuration is missing. OSS functionality will be disabled.");
        }
    }

    @PreDestroy
    public void destroy() {
        if (ossClient != null) {
            ossClient.shutdown();
            log.info("OSS client closed");
        }
    }

    public boolean isAvailable() {
        return ossClient != null && StringUtils.hasText(bucketName);
    }

    public String getCDNUrl(String objectName) {
        return CDNPoint + "/" + normalizeObjectName(objectName);
    }

    public String getObjectUrl(String objectName) {
        validateAvailable();

        String normalizedObjectName = normalizeObjectName(objectName);
        if (StringUtils.hasText(CDNPoint)) {
            return CDNPoint.replaceAll("/+$", "") + "/" + normalizedObjectName;
        }

        String normalizedEndpoint = endPoint.replaceFirst("^https?://", "");
        return "https://" + bucketName + "." + normalizedEndpoint + "/" + normalizedObjectName;
    }

    public boolean doesObjectExist(String objectName) {
        validateAvailable();
        return ossClient.doesObjectExist(bucketName, normalizeObjectName(objectName));
    }

    public String generatePresignedUrl(String objectName, int expireTime, String method) {
        return generatePresignedUrl(objectName, expireTime, method, null);
    }

    public String generatePresignedUrl(String objectName, int expireTime, String method, String contentType) {
        try {
            validateAvailable();
            GeneratePresignedUrlRequest request =
                    new GeneratePresignedUrlRequest(bucketName, normalizeObjectName(objectName));
            request.setMethod(com.aliyun.oss.HttpMethod.valueOf(method));
            request.setExpiration(new Date(System.currentTimeMillis() + expireTime * 1000L));
            if (StringUtils.hasText(contentType)) {
                request.setContentType(contentType);
            }

            URL url = ossClient.generatePresignedUrl(request);
            return url.toString();
        } catch (Exception e) {
            log.error("Failed to generate presigned URL", e);
            throw new RuntimeException("生成签名URL失败", e);
        }
    }

    private void validateAvailable() {
        if (!isAvailable()) {
            throw new IllegalStateException("Aliyun OSS 未正确配置");
        }
    }

    private String normalizeObjectName(String objectName) {
        return objectName == null ? "" : objectName.replaceFirst("^/+", "");
    }
}
