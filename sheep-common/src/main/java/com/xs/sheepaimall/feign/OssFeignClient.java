package com.xs.sheepaimall.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * OSS 文件服务 Feign 接口 — 仅对内暴露，供其他模块调用上传/删除
 */
@FeignClient(name = "sheep-product", contextId = "ossClient", path = "/internal/file")
public interface OssFeignClient {

    @PostMapping("/upload-base64")
    String uploadBase64(@RequestParam String base64Data, @RequestParam String type);

    @DeleteMapping("/delete-by-url")
    void deleteByUrl(@RequestParam String fileUrl);
}
