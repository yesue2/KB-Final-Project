package com.kb.board.controller;

import com.kb.board.dto.*;
import com.kb.board.service.BoardService;
import com.kb.member.dto.Member;
import com.kb.member.service.MemberService;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/board")
@RequiredArgsConstructor
@Slf4j
@Api(value = "BoardController", tags = "게시판 정보")
@PropertySource({"classpath:/application.properties"})
public class BoardController {

    @Autowired
    private BoardService boardService;

    @Autowired
    private MemberService memberService;

    @Value("#{'${os_type}' == 'win' ? '${file_save_location_win}/board':'${file_save_location_other}/board'}")
    public String BASE_DIR;

    private final BoardService service;

    @GetMapping("/types")
    public ResponseEntity<List<BoardCategory>> getTypes() {
        List<BoardCategory> types = service.getCategoryList();
        return ResponseEntity.ok(types);
    }

    @GetMapping("") // 게시판 목록 내용 조회
    public ResponseEntity<BoardPageResult> getList(BoardParam boardParam) {
        BoardPageResult result = service.getBoardList(boardParam);
        return ResponseEntity.ok(result);
    }
    @GetMapping("/my/{memberId}") // 게시판 목록 내용 조회
    public ResponseEntity<List<BoardPost>> mySelectPostList(@PathVariable String memberId) {
        List<BoardPost> result = service.mySelectPostList(memberId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{bno}/posts") // 게시글 bno 별 리스트 조회
    public ResponseEntity<BoardPostPageResult> getPosts(@PathVariable int bno, PostParam postParam) {
        postParam.setBoardId(bno);  // 게시판 ID 설정
        postParam.setBno(bno);      // 게시글 번호 추가
        BoardPostPageResult postResult = service.getPostList(postParam);

        // 각 게시글에 좋아요 수 및 작성자 ID 추가
        List<BoardPost> postsWithAuthorsAndLikes = postResult.getPostList().stream()
                .map(post -> {
                    BoardPost detailedPost = service.getPostWithLikesCount(post.getPostId()); // 좋아요 수 포함
                    Member member = memberService.findByMno((int) detailedPost.getMno()); // mno로 Member 조회
                    detailedPost.setAuthorId(member.getId()); // 작성자 ID 설정
                    return detailedPost; // 수정된 BoardPost 객체 반환
                })
                .collect(Collectors.toList());

        postResult.setPostList(postsWithAuthorsAndLikes); // 수정된 리스트 업데이트

        return ResponseEntity.ok(postResult);
    }

//    @GetMapping("/{postId}") // 게시글 조회
//    public ResponseEntity<BoardPost> getById(@PathVariable long postId) {
//        return ResponseEntity.ok(service.getBoard(postId));
//    }

    @PostMapping("/{type}") // 게시판 글 작성
    public ResponseEntity<BoardPost> create(
            @PathVariable String type, // URL의 마지막 부분에서 type 받기
            @ModelAttribute @Valid BoardDTO boardDTO,
            @RequestParam(name = "files", required = false) List<MultipartFile> files,
            @AuthenticationPrincipal Member principal) {

        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        int bno;
        // type에 따라 bno 값 설정 (type이 문자열이므로 직접 매핑)
        switch (type) {
            case "IPWC":
            case "IPMC":
            case "IBWC":
            case "IBMC":
                bno = 1; // 안정형
                break;
            case "IPML":
            case "IPWL":
            case "IBML":
            case "IBWL":
                bno = 2; // 중립형
                break;
            case "APWL":
            case "APML":
            case "ABWC":
            case "APMC":
                bno = 3; // 적극투자형
                break;
            case "ABWL":
            case "APWC":
            case "ABMC":
            case "ABML":
                bno = 4; // 공격투자형
                break;
            default:
                return ResponseEntity.badRequest().body(null); // 잘못된 type인 경우
        }

        // BoardDTO에 bno와 type 설정
        boardDTO.setBno(bno);
        boardDTO.setType(type);

        BoardPost boardPost = boardDTO.toBoardPost(); // DTO를 게시글 객체로 변환
        boardPost.setMno(principal.getMno());
        boardPost.setAuthorId(String.format("%d", principal.getMno()));

        log.info("Creating boardPost with bno: " + boardPost.getPostId());

        try {
            BoardPost createdPost = boardService.createBoardPost(boardPost, files);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdPost);
        } catch (Exception e) {
            log.error("Error while creating board post", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @PutMapping("/{postId}")
    public ResponseEntity<BoardPost> update(@PathVariable Long postId,
                                            @RequestBody BoardDTO boardDTO,
                                            @RequestParam(name = "files", required = false) List<MultipartFile> files) {
        try {
            BoardPost boardPost = boardDTO.toBoardPost();
            boardPost.setPostId(postId); // 게시글 번호를 postId로 설정
            System.out.println("files " + files);
            return ResponseEntity.ok(service.updateBoard(boardPost, files));
        } catch (Exception e) {
            log.error("Error updating board post: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }



    @DeleteMapping("/{postId}")
    public ResponseEntity<BoardPost> delete(@PathVariable long postId) {
        return ResponseEntity.ok(service.deleteBoard(postId));
    }
    @GetMapping("/file/{fileName}")
    @ResponseBody
    public ResponseEntity<Resource> downloadImage(@PathVariable("fileName") String fileName) {
        try {
            Path filePath = Paths.get(BASE_DIR).resolve(fileName).normalize();
            log.info("Attempting to serve file from path: {}", filePath.toString());

            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                log.warn("File not found or not readable: {}", filePath.toString());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }

            String contentType = "application/octet-stream";
            try {
                contentType = Files.probeContentType(filePath);
            } catch (IOException e) {
                log.warn("Could not determine file type for {}", filePath);
            }

            log.info("Serving file with content type: {}", contentType);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);
        } catch (MalformedURLException e) {
            log.error("Malformed URL: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    // 좋아요 버튼
    @PostMapping("/{postId}/like")
    public ResponseEntity<BoardPost> likePost(@PathVariable Long postId, @AuthenticationPrincipal Member principal) {

        int mno = principal.getMno();
        // 사용자가 이미 좋아요를 눌렀는지 확인
        boolean alreadyLiked = boardService.checkLikeExists(postId, mno);

        if (alreadyLiked) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null);
        }
        boardService.addLike(postId, mno);

        BoardPost updatedPost = boardService.getPostWithLikesCount(postId);
        return ResponseEntity.ok(updatedPost);
    }


    // 좋아요 수 가져오기
//    @GetMapping("/{postId}/likes")
//    public ResponseEntity<Integer> getLikesCount(@PathVariable Long postId) {
//        int likesCount = boardService.getLikesCount(postId);
//        return ResponseEntity.ok(likesCount);
//    }




//
//    // 이미지 출력
//    @GetMapping("/file/{fileName}")
//    @ResponseBody
//    public Resource downloadImage(@PathVariable("fileName") String fileName) throws Exception {
//        return new UrlResource("file:" + BASE_DIR + fileName);
//    }

    @DeleteMapping("/attachment/{fno}")
    public ResponseEntity<Boolean> deleteAttachment(@PathVariable long fno) throws Exception {
        return ResponseEntity.ok(service.deleteAttachment(fno));
    }


    @PostMapping("/replyPlus/{postId}")
    public ResponseEntity<BoardReply> makeReply(
            @PathVariable long postId,
            @RequestBody BoardReply reply,
            @AuthenticationPrincipal Member member) throws Exception {

        BoardReply createdReply = service.createReply(postId, reply, member);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdReply);
    }


    @DeleteMapping("/reply/{rno}")
    public ResponseEntity<BoardReply> deleteReply(
            @PathVariable int rno,
            @RequestBody BoardReplyDTO replyDTO,
            @AuthenticationPrincipal Member principal) throws Exception {

        if (principal == null) {
            throw new IllegalAccessException("User is not authenticated");
        }

        BoardReply reply = service.getReply(rno);
        if (reply == null) {
            throw new NoSuchElementException("Reply not found for rno: " + rno);
        }

        if (!reply.getMemberId().equals(principal.getId())) {
            throw new IllegalAccessException("User does not have permission to delete this reply");
        }

        int result = service.deleteReply(rno);
        if (result != 1) {
            throw new Exception("DB error");
        }

        return ResponseEntity.ok(reply);
    }

    @GetMapping("/reply/{postId}")
    public ResponseEntity<List<BoardReply>> getReply(@PathVariable long postId) {
        return ResponseEntity.ok(service.selectReplyByBno(postId));
    }
}

