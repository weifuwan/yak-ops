package io.yak.ops.api.dto.resources;

import io.yak.ops.dao.entity.User;

import org.springframework.web.multipart.MultipartFile;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateFileRequest {

    private User loginUser;

    private String fileAbsolutePath;

    private MultipartFile file;

}
