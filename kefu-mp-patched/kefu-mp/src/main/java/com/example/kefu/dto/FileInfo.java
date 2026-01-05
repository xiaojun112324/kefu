// FileInfo.java
package com.example.kefu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data @AllArgsConstructor
public class FileInfo {
    private String url;         // 例: https://xx/file/12345/uuid.png
    private String fileName;    // 原始文件名
    private long   size;        // 字节
    private String contentType; // MIME，如 image/png
    private Integer ct;         // 1文本 2图片 3视频 4文件（供前端直接用）
}
