package com.xs.sheepaimall.util;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.xs.sheepaimall.common.BizException;
import com.xs.sheepaimall.config.AliOssProperties;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

/**
 * 阿里云 OSS 文件上传工具类
 */
@Slf4j
public class OssUtil {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final Set<String> ALLOWED_TYPES = Set.of("avatar", "goods", "cert");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OSS ossClient;
    private final AliOssProperties aliOssProperties;

    public OssUtil(AliOssProperties aliOssProperties) {
        this.aliOssProperties = aliOssProperties;
        this.ossClient = new OSSClientBuilder().build(
                aliOssProperties.getEndpoint(),
                aliOssProperties.getAccessKeyId(),
                aliOssProperties.getAccessKeySecret());
    }

    /** 上传二进制图片 */
    public String upload(byte[] bytes, String type) {
        validateType(type);
        String ext = "jpg";
        return putToOss(bytes, type, ext);
    }

    /** 上传 MultipartFile 图片 */
    public String upload(org.springframework.web.multipart.MultipartFile file, String type) {
        if (file == null || file.isEmpty()) {
            throw new BizException("上传文件不能为空");
        }
        validateType(type);
        String ext = extractExtension(file.getOriginalFilename());
        validateExtension(ext);
        try {
            return putToOss(file.getBytes(), type, ext);
        } catch (Exception e) {
            log.error("上传失败", e);
            throw new BizException("上传失败");
        }
    }

    /** 上传 Base64 格式图片 */
    public String uploadBase64(String base64Data, String type) {
        if (base64Data == null || base64Data.isBlank()) {
            throw new BizException("图片数据不能为空");
        }
        validateType(type);

        String pureData;
        String ext;
        if (base64Data.contains(";base64,")) {
            String header = base64Data.substring(0, base64Data.indexOf(";base64,"));
            ext = header.contains("/") ? header.substring(header.lastIndexOf('/') + 1) : "jpg";
            pureData = base64Data.substring(base64Data.indexOf(";base64,") + 8);
        } else {
            ext = "jpg";
            pureData = base64Data;
        }
        validateExtension(ext);

        try {
            byte[] bytes = Base64.getDecoder().decode(pureData);
            return putToOss(bytes, type, ext);
        } catch (IllegalArgumentException e) {
            throw new BizException("Base64 格式无效");
        }
    }

    /** 删除 OSS 文件 */
    public void deleteFile(String objectName) {
        try {
            ossClient.deleteObject(aliOssProperties.getBucketName(), objectName);
            log.debug("OSS删除成功: {}", objectName);
        } catch (Exception e) {
            log.error("OSS删除失败: {}", objectName, e);
        }
    }

    /**
     * 从 OSS 完整 URL 中提取 objectName 并删除。
     * URL 格式: https://{bucket}.{endpoint}/{objectName}
     */
    public void deleteByUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return;
        String prefix = "https://" + aliOssProperties.getBucketName() + "." + aliOssProperties.getEndpoint() + "/";
        if (fileUrl.startsWith(prefix)) {
            String objectName = fileUrl.substring(prefix.length());
            deleteFile(objectName);
        }
    }

    private String putToOss(byte[] data, String type, String ext) {
        String dateDir = LocalDate.now().format(DATE_FMT);
        String objectName = type + "/" + dateDir + "/" + UUID.randomUUID().toString().replace("-", "") + "." + ext;

        try {
            ossClient.putObject(aliOssProperties.getBucketName(), objectName, new ByteArrayInputStream(data));
            log.debug("OSS上传成功: {}", objectName);
        } catch (Exception e) {
            log.error("OSS上传失败", e);
            throw new BizException("上传失败");
        }

        return "https://" + aliOssProperties.getBucketName() + "." + aliOssProperties.getEndpoint() + "/" + objectName;
    }

    private void validateType(String type) {
        if (type == null || !ALLOWED_TYPES.contains(type)) {
            throw new BizException("不支持的图片分类，仅支持: " + ALLOWED_TYPES);
        }
    }

    private void validateExtension(String ext) {
        if (ext == null || !ALLOWED_EXTENSIONS.contains(ext.toLowerCase())) {
            throw new BizException("不支持的图片格式，仅支持: " + ALLOWED_EXTENSIONS);
        }
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new BizException("无法识别文件格式");
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
