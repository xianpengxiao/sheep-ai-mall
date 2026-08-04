package com.xs.sheepaimall.controller;

import com.xs.sheepaimall.util.OssUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 文件服务内部控制器（供 Feign 调用）
 */
@RestController
@RequestMapping("/internal/file")
public class InternalFileController {

    @Autowired
    private OssUtil ossUtil;

    @PostMapping("/upload-base64")
    public String uploadBase64(@RequestParam String base64Data, @RequestParam String type) {
        return ossUtil.uploadBase64(base64Data, type);
    }

    @DeleteMapping("/delete-by-url")
    public void deleteByUrl(@RequestParam String fileUrl) {
        ossUtil.deleteByUrl(fileUrl);
    }
}
