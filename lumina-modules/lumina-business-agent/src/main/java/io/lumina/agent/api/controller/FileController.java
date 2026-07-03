package io.lumina.agent.api.controller;

import io.lumina.common.core.R;
import io.lumina.framework.storage.FileService;
import io.lumina.framework.storage.FileVO;
import io.lumina.framework.storage.entity.FileDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 文件管理 Controller
 *
 * @author Lumina Team
 * @since 1.3.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    @Autowired
    private FileService fileService;

    /**
     * 上传文件
     */
    @PostMapping("/upload")
    public R<FileVO> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "bizType", required = false, defaultValue = "general") String bizType) {
        log.info("文件上传: name={}, size={}, bizType={}", file.getOriginalFilename(), file.getSize(), bizType);

        if (file.isEmpty()) {
            return R.fail(400, "文件不能为空");
        }

        FileDO fileDO = fileService.upload(file, bizType);

        FileVO vo = new FileVO();
        vo.setFileUuid(fileDO.getFileUuid());
        vo.setOriginalName(fileDO.getOriginalName());
        vo.setContentType(fileDO.getContentType());
        vo.setFileSize(fileDO.getFileSize());
        vo.setUrl("/api/v1/files/" + fileDO.getFileUuid() + "/download");
        return R.success(vo);
    }

    /**
     * 获取文件元数据
     */
    @GetMapping("/{fileUuid}")
    public R<FileVO> getFileInfo(@PathVariable String fileUuid) {
        FileDO fileDO = fileService.getByUuid(fileUuid);
        if (fileDO == null) {
            return R.fail(404, "文件不存在");
        }
        FileVO vo = new FileVO();
        vo.setFileUuid(fileDO.getFileUuid());
        vo.setOriginalName(fileDO.getOriginalName());
        vo.setContentType(fileDO.getContentType());
        vo.setFileSize(fileDO.getFileSize());
        vo.setUrl("/api/v1/files/" + fileDO.getFileUuid() + "/download");
        return R.success(vo);
    }

    /**
     * 下载文件
     */
    @GetMapping("/{fileUuid}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable String fileUuid) {
        FileDO fileDO = fileService.getByUuid(fileUuid);
        if (fileDO == null) {
            return ResponseEntity.notFound().build();
        }

        InputStream stream = fileService.download(fileUuid);
        String encodedName = URLEncoder.encode(fileDO.getOriginalName(), StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .contentType(MediaType.parseMediaType(
                        fileDO.getContentType() != null ? fileDO.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE))
                .contentLength(fileDO.getFileSize())
                .body(new InputStreamResource(stream));
    }

    /**
     * 删除文件
     */
    @DeleteMapping("/{fileUuid}")
    public R<Void> delete(@PathVariable String fileUuid) {
        fileService.delete(fileUuid);
        return R.success();
    }
}
