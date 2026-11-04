package com.ticket_system.manage_revenue_ticket.service;

import com.ticket_system.common.exception.ResourceNotFoundException;
import com.ticket_system.manage_revenue_ticket.Dto.response.AvatarResponse;
import com.ticket_system.manage_revenue_ticket.config.MinioProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AvatarServiceTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};
    static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8'};

    private final S3Client s3Client = mock(S3Client.class);
    private final AvatarService service = new AvatarService(s3Client,
            new MinioProperties("http://localhost:9010", "app", "app-secret", "user-avatars"));

    @Test
    void uploadStoresUnderTheUsersKeyWithTheTypeFromTheBytesNotTheHeader() throws Exception {
        // The client claims text/plain; the bytes are a PNG, and that is what gets stored.
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", PNG);

        AvatarResponse response = service.upload(7L, file);

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client).putObject(request.capture(), body.capture());
        // the exact bytes are stored, not an empty or truncated body
        try (var stored = body.getValue().contentStreamProvider().newStream()) {
            assertThat(stored.readAllBytes()).isEqualTo(PNG);
        }
        assertThat(request.getValue().bucket()).isEqualTo("user-avatars");
        assertThat(request.getValue().key()).isEqualTo("avatars/7");
        assertThat(request.getValue().contentType()).isEqualTo("image/png");
        assertThat(response).isEqualTo(new AvatarResponse("image/png", PNG.length));
    }

    @Test
    void nonImageIsRejectedEvenWhenTheHeaderSaysImage() {
        MockMultipartFile file = new MockMultipartFile("file", "x.png", "image/png", "<svg onload=alert(1)>".getBytes());

        assertThatThrownBy(() -> service.upload(7L, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Chỉ nhận ảnh JPEG, PNG hoặc WEBP");
        verifyNoInteractions(s3Client);
    }

    @Test
    void emptyOrMissingFileIsRejected() {
        assertThatThrownBy(() -> service.upload(7L, new MockMultipartFile("file", new byte[0])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Chưa chọn ảnh");
        assertThatThrownBy(() -> service.upload(7L, null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(s3Client);
    }

    @Test
    void fileOverTwoMegabytesIsRejected() {
        byte[] big = Arrays.copyOf(JPEG, (int) AvatarService.MAX_BYTES + 1);

        assertThatThrownBy(() -> service.upload(7L, new MockMultipartFile("file", "big.jpg", "image/jpeg", big)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ảnh tối đa 2MB");
        verifyNoInteractions(s3Client);
    }

    @Test
    void fileOfExactlyTwoMegabytesIsAccepted() {
        byte[] max = Arrays.copyOf(JPEG, (int) AvatarService.MAX_BYTES);

        assertThat(service.upload(7L, new MockMultipartFile("file", max)).size()).isEqualTo(AvatarService.MAX_BYTES);
    }

    @Test
    void detectsJpegPngWebpByMagicBytes() {
        assertThat(AvatarService.detectImageType(JPEG)).isEqualTo("image/jpeg");
        assertThat(AvatarService.detectImageType(PNG)).isEqualTo("image/png");
        assertThat(AvatarService.detectImageType(WEBP)).isEqualTo("image/webp");
        assertThat(AvatarService.detectImageType("GIF89a".getBytes())).isNull();
        assertThat(AvatarService.detectImageType(new byte[]{(byte) 0xFF})).isNull();
        // RIFF container that is not WEBP (WAV audio) and cut-off PNG / WEBP headers
        assertThat(AvatarService.detectImageType(
                new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'})).isNull();
        assertThat(AvatarService.detectImageType(Arrays.copyOf(PNG, 7))).isNull();
        assertThat(AvatarService.detectImageType(Arrays.copyOf(WEBP, 11))).isNull();
    }

    @Test
    void unreadableUploadIsNotStored() throws Exception {
        MockMultipartFile broken = new MockMultipartFile("file", PNG) {
            @Override
            public byte[] getBytes() throws java.io.IOException {
                throw new java.io.IOException("stream closed");
            }
        };

        assertThatThrownBy(() -> service.upload(7L, broken)).isInstanceOf(java.io.UncheckedIOException.class);
        verifyNoInteractions(s3Client);
    }

    @Test
    void downloadTypeComesFromTheBytesNotTheStoredHeader() {
        // An object written outside the app (console, mc) may claim any type.
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(ResponseBytes.fromByteArray(
                GetObjectResponse.builder().contentType("text/html").build(), PNG));

        assertThat(service.download(7L).contentType()).isEqualTo("image/png");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void storedTypeCannotForgeLogLines(CapturedOutput output) {
        // The stored Content-Type is metadata from whoever wrote the object; CR/LF must not start a new log line.
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(ResponseBytes.fromByteArray(
                GetObjectResponse.builder().contentType("text/html\r\nFAKE LOG LINE").build(), "<html>".getBytes()));

        assertThatThrownBy(() -> service.download(7L)).isInstanceOf(IllegalStateException.class);
        assertThat(output).contains("storedType=text/htmlFAKE LOG LINE").doesNotContain("\nFAKE LOG LINE");
    }

    @Test
    void downloadOfAnObjectThatIsNotAnImageFails() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(ResponseBytes.fromByteArray(
                GetObjectResponse.builder().contentType("image/png").build(), "<script>x</script>".getBytes()));

        assertThatThrownBy(() -> service.download(7L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void downloadReturnsStoredBytesAndType() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(ResponseBytes.fromByteArray(
                GetObjectResponse.builder().contentType("image/png").build(), PNG));

        AvatarService.Avatar avatar = service.download(7L);

        assertThat(avatar.content()).isEqualTo(PNG);
        assertThat(avatar.contentType()).isEqualTo("image/png");
        ArgumentCaptor<GetObjectRequest> request = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObjectAsBytes(request.capture());
        assertThat(request.getValue().key()).isEqualTo("avatars/7");
    }

    @Test
    void downloadWithoutAvatarIsNotFound() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("missing").build());

        assertThatThrownBy(() -> service.download(7L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Chưa có ảnh đại diện");
    }
}
