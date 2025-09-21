package top.contins.authservice.util;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.MatchMode;
import com.aliyun.oss.model.PolicyConditions;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.auth.sts.AssumeRoleRequest;
import com.aliyuncs.auth.sts.AssumeRoleResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.profile.DefaultProfile;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class AliOssUtil {
    @Value("${CDNPoint}")
    private String CDNPoint;

    @Value("${aliyun.oss.endpoint}")
    private String endPoint;

    @Value("${aliyun.oss.bucketName}")
    private String bucketName;

    @Value("${aliyun.oss.accessKeyId}")
    private String accessKeyId;

    @Value("${aliyun.oss.accessKeySecret}")
    private String accessKeySecret;

    @Value("${aliyun.oss.roleArn}")
    private String roleArn;

    private OSS ossClient;

    @PostConstruct
    public void init() {
        ossClient = new OSSClientBuilder().build(endPoint, accessKeyId, accessKeySecret);
    }

    @PreDestroy
    public void destroy() {
        if (ossClient != null) {
            ossClient.shutdown();
            log.info("OSS 客户端已关闭");
        }
    }

    public String getCDNUrl(String objectName) {
        return CDNPoint + "/" + objectName;
    }

    /**
     * 生成签名URL（用于上传或下载文件）
     *
     * @param objectName 文件在OSS中的路径（例如：exampleDir/exampleObject.png）
     * @param expireTime URL的有效时间（单位：秒）
     * @param method     HTTP方法（"PUT"表示上传，"GET"表示下载）
     * @return 签名URL
     */
    public String generatePresignedUrl(String objectName, int expireTime, String method) {
        try {
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucketName, objectName);
            request.setMethod(com.aliyun.oss.HttpMethod.valueOf(method));
            request.setExpiration(new Date(System.currentTimeMillis() + expireTime * 1000L));
            URL url = ossClient.generatePresignedUrl(request);
            return url.toString();
        } catch (Exception e) {
            log.error("生成签名URL失败", e);
            throw new RuntimeException("生成签名URL失败", e);
        }
    }
}
