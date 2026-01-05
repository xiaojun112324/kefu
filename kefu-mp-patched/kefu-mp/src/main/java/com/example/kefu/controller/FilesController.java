package com.example.kefu.controller;

import com.example.kefu.common.ApiResp;
import com.example.kefu.dto.FileInfo;
import com.example.kefu.security.AuthContext;
import com.example.kefu.security.HeaderAuthInterceptor;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/files")
public class FilesController {
    private static final String ROOT_DIR="/www/wwwroot/file";
    private static final String URL_PREFIX="/file";

    @PostMapping(path="/brand-logo", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResp<FileInfo> uploadBrandLogo(@RequestPart("file") MultipartFile file){
        AuthContext ctx = HeaderAuthInterceptor.CTX.get();
        if (ctx==null || ctx.getUserId()==null) return ApiResp.fail("UNAUTHORIZED");
        if (file==null || file.isEmpty()) return ApiResp.fail("no file");

        try{
            Long uid = ctx.getUserId();
            Path dir = Path.of(ROOT_DIR, "brand", String.valueOf(uid));
            if (Files.notExists(dir)) Files.createDirectories(dir);

            String original = Optional.ofNullable(file.getOriginalFilename()).orElse("logo");
            String ext = Optional.ofNullable(StringUtils.getFilenameExtension(original)).orElse("").toLowerCase();
            if (!Set.of("png","jpg","jpeg","webp","gif","svg").contains(ext)) return ApiResp.fail("仅支持图片：png/jpg/jpeg/webp/gif/svg");

            String name = System.currentTimeMillis()+"_"+UUID.randomUUID().toString().replace("-","")+(ext.isEmpty()?"":"."+ext);
            Path dest = dir.resolve(name);
            try (InputStream in = file.getInputStream()){ Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING); }

            String mime = Optional.ofNullable(Files.probeContentType(dest)).orElse("image/"+(ext.isEmpty()?"png":ext));
            String url  = URL_PREFIX + "/brand/" + uid + "/" + name;
            return ApiResp.ok(new FileInfo(url, original, Files.size(dest), mime, 2)); // ct=2 图片
        }catch(Exception e){
            e.printStackTrace();
            return ApiResp.fail("upload error");
        }
    }
}