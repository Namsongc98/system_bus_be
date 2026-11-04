package com.ticket_system.manage_revenue_ticket.controller;

import com.ticket_system.common.Dto.response.BaseResponseDto;
import com.ticket_system.manage_revenue_ticket.Dto.response.AvatarResponse;
import com.ticket_system.manage_revenue_ticket.service.AvatarService;
import com.ticket_system.manage_revenue_ticket.service.AvatarService.Avatar;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * The caller's own avatar. Any logged-in role; "id" is set from the JWT by AuthInterceptor, so a
 * user can only read or replace their own. MinIO is not public: the file is streamed through here.
 */
@RestController
@RequestMapping("/api/profile/avatar")
public class ProfileAvatarController {

    private final AvatarService avatarService;

    public ProfileAvatarController(AvatarService avatarService) {
        this.avatarService = avatarService;
    }

    /** Create or replace: multipart/form-data with one part named "file". */
    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BaseResponseDto<AvatarResponse>> upload(
            @RequestAttribute("id") Long userId,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(BaseResponseDto.success(200, "Upload avatar successfully",
                avatarService.upload(userId, file)));
    }

    @GetMapping
    public ResponseEntity<byte[]> download(@RequestAttribute("id") Long userId) {
        Avatar avatar = avatarService.download(userId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(avatar.contentType()))
                .cacheControl(CacheControl.noCache().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(avatar.content());
    }
}
