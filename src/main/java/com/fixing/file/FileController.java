package com.fixing.file;

import com.fixing.common.BusinessException;
import com.fixing.common.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 文件上传（M12 最小版）：报修的故障图片/视频。
 *
 * <p>v0.3 存本地磁盘（uploads/ 目录），返回可访问的 URL；
 * 上线阶段换 MinIO/OSS —— 只改这个类的存储实现，接口契约不变。
 *
 * <p>安全三件事，一件不能少：
 * 1. 扩展名白名单 —— 只收图片/视频，杜绝有人传 .jsp/.sh 上来；
 * 2. 文件名服务端重新生成（UUID）—— 原始文件名可能带路径穿越(../)或脚本；
 * 3. 大小限制 —— 在 application.yml 的 multipart 配置里（100MB），防止磁盘被灌满。
 */
@RestController
public class FileController {

    /** 允许的扩展名：图片 + 常见视频格式 */
    private static final Set<String> ALLOWED_EXT =
            Set.of("jpg", "jpeg", "png", "gif", "webp", "mp4", "mov", "webm");

    private final Path uploadDir;

    /** 上传目录从配置读（默认 ./uploads），启动时确保存在 */
    public FileController(@Value("${fixing.upload-dir:uploads}") String dir) throws IOException {
        this.uploadDir = Path.of(dir).toAbsolutePath();
        Files.createDirectories(uploadDir);
    }

    /**
     * 上传一个文件，返回 {"url": "/files/xxx.jpg"}。
     * 前端先调这里拿 URL，再把 URL 放进报修请求的 photos 数组。
     */
    @PostMapping("/files")
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new BusinessException("文件为空");
        }
        // 取原始文件名里的扩展名并校验白名单（大小写不敏感）
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String ext = dot < 0 ? "" : original.substring(dot + 1).toLowerCase();
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException("只支持图片(jpg/png/gif/webp)或视频(mp4/mov/webm)，收到: ." + ext);
        }
        // 服务端生成文件名：UUID 防重名、防路径穿越、防脚本名
        String filename = UUID.randomUUID() + "." + ext;
        file.transferTo(uploadDir.resolve(filename));
        // 返回的 URL 由 WebConfig 的资源映射提供访问（/files/** → uploads/ 目录）
        return Result.ok(Map.of("url", "/files/" + filename));
    }
}
