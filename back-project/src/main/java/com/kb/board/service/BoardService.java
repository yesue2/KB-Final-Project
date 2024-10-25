package com.kb.board.service;

import com.kb.board.dto.*;
import com.kb.board.mapper.BoardMapper;
import com.kb.common.pagination.PageInfo;
import com.kb.common.util.UploadFiles;
import com.kb.member.dto.Member;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j;
import org.apache.ibatis.annotations.Param;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

@Log4j
@Service
@RequiredArgsConstructor
@PropertySource({"classpath:/application.properties"})
public class BoardService {
    @Value("#{'${os_type}' == 'win' ? '${file_save_location_win}/board':'${file_save_location_other}/board'}")
    public String BASE_DIR;

    private final BoardMapper mapper;
    private static List<BoardCategory> categoryList;
    private static final int PAGE_LIMIT = 5;
    private static final int LIST_LIMIT = 12; // 게시글 보여질 list 수

    public List<BoardCategory> getCategoryList() {
        if (categoryList == null) {
            categoryList = mapper.selectBoardCategory();
        }
        return categoryList;
    }

    public BoardPageResult getBoardList(BoardParam boardParam) {
        int totalSize = mapper.selectBoardCount(boardParam);
        int listLimit = boardParam.getAmount() == 0 ? LIST_LIMIT : boardParam.getAmount();
        PageInfo pageInfo = new PageInfo(boardParam.getPage(), totalSize, listLimit, PAGE_LIMIT);
        boardParam.setLimit(pageInfo.getListLimit());
        boardParam.setOffset(pageInfo.getStartList() - 1);
        List<BoardPost> boardList = mapper.selectBoardList(boardParam);
        if (boardList == null || boardList.isEmpty()) {
            boardList = new ArrayList<>();
        }
        return new BoardPageResult(boardList, boardParam, pageInfo, getCategoryList(),totalSize);
    }

    public BoardPostPageResult getPostList(PostParam postParam) {
        // 총 게시글 수 조회
        int totalSize = mapper.selectPostCount(postParam);

        // 페이지당 게시글 수 설정
        int listLimit = postParam.getLimit() == 0 ? LIST_LIMIT : postParam.getLimit();
        PageInfo pageInfo = new PageInfo(postParam.getPage(), totalSize, listLimit, PAGE_LIMIT);

        // SQL 쿼리를 위한 limit과 offset 설정
        postParam.setLimit(pageInfo.getListLimit());
        postParam.setOffset(pageInfo.getStartList() - 1);

        // 게시글 리스트 조회
        List<BoardPost> postList = mapper.selectPostList(postParam);
        if (postList == null || postList.isEmpty()) {
            postList = new ArrayList<>();
        }

        // 결과 반환
        return new BoardPostPageResult(postList, pageInfo, totalSize);
    }


    @Transactional
    public BoardPost getBoard(Long postId) {
        log.info("Getting post with ID: " + postId);
        BoardPost boardPost = mapper.selectBoardPostByPostId(postId);

        // null 체크
        if (boardPost == null) {
            throw new NoSuchElementException("No post found with id: " + postId);
        }

        // 조회수 증가
        boardPost.setReadCount(boardPost.getReadCount() + 1);
        mapper.updateReadCount(boardPost);

        return boardPost;
    }

    @Transactional(rollbackFor = Exception.class)
    public BoardPost createBoardPost(BoardPost boardPost, List<MultipartFile> files) {
        // 먼저 authorId 가져오기
        String authorId = mapper.getAuthorIdByMno(boardPost.getMno());
        boardPost.setAuthorId(authorId);

        // createdDate를 현재 시간으로 설정
        boardPost.setCreatedDate(new Timestamp(System.currentTimeMillis()));

        log.info("Creating post: " + boardPost);

        // 게시글 삽입 전 로그 추가
        log.info("Inserting into board_post with postId: " + boardPost.getPostId());

        // 게시글 작성
        int result = mapper.insertBoardPost(boardPost);
        if (result != 1) {
            throw new NoSuchElementException("Failed to insert board post.");
        }

        long generatedPostId = boardPost.getPostId(); // 게시글 ID 가져오기

        // 파일 업로드 처리 (필요한 경우)
        if (files != null && !files.isEmpty()) {
            upload(generatedPostId, files);
        }

        return boardPost; // 생성된 게시글 반환
    }


    private void upload(long postId, List<MultipartFile> files) {
        for (MultipartFile part : files) {
            if (part.isEmpty()) continue;
            try {
                String renameFileName = UploadFiles.upload(BASE_DIR, part);
                BoardAttachFile attach = new BoardAttachFile(0, postId, part.getOriginalFilename(), renameFileName, part.getContentType(), part.getSize(), null);
                mapper.insertAttachFile(attach);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void deleteFile(String savePath, BoardAttachFile boardAttachFile) {
        File file = new File(savePath + "/" + boardAttachFile.getRenamedFilename());
        if (file.exists()) {
            file.delete();
        }
    }


    @Transactional // 게시글 수정
    public BoardPost updateBoard(BoardPost boardPost, List<MultipartFile> files) {
        log.info("update...... " + boardPost);
        log.info("Updating board post with postId: " + boardPost.getPostId());

        // 게시글 존재 여부 확인
        BoardPost existingPost = mapper.selectBoardPostByPostId(boardPost.getPostId());
        log.info("Existing post found: " + existingPost);

        if (existingPost == null) {
            throw new NoSuchElementException("No post found with id: " + boardPost.getPostId());
        }

        int result = mapper.updateBoardPost(boardPost);
        if (result != 1) {
            throw new RuntimeException("Failed to update post with id: " + boardPost.getPostId());
        }

        // 파일 업로드 처리
        if (files != null && !files.isEmpty()) {
            upload(boardPost.getPostId(), files);
        }

        return getBoard(boardPost.getPostId());
    }


    @Transactional // 게시글 삭제
    public BoardPost deleteBoard(long postId) {
        log.info("delete...." + postId);
        BoardPost boardPost = getBoard(postId);

        List<BoardAttachFile> oldFiles = boardPost.getBoardAttachFileList();
        if (oldFiles != null) { // null 체크 추가
            for (BoardAttachFile old : oldFiles) {
                deleteFile(BASE_DIR, old);
            }
        }

        int result = mapper.deleteBoard(postId);
        if (result != 1) {
            throw new NoSuchElementException();
        }
        return boardPost;
    }

    public BoardAttachFile getAttachment(long postId) {
        return mapper.selectAttachFileByFno(postId);
    }

    public boolean deleteAttachment(long fno) {
        BoardAttachFile attachFile =  mapper.selectAttachFileByFno(fno);
        deleteFile(BASE_DIR, attachFile);
        return mapper.deleteAttachFile(fno) == 1;
    }

    public BoardReply getReply(int rno) {
        return mapper.selectReplyByRno(rno);
    }

    public int deleteReply(int rno) {
        return mapper.deleteReply(rno);
    }

    public boolean checkLikeExists(Long postId, int mno) {
        int count = mapper.checkLikeExists(postId, mno);
        return count > 0;
    }

    @Transactional
    public void addLike(Long postId, int memberId) {
        mapper.insertLike(postId, memberId); // likes 테이블에 레코드 추가
        mapper.incrementLikesCount(postId);  // likes_count 증가
    }

    public BoardPost getPostWithLikesCount(Long postId) {
        BoardPost post = mapper.getBoardPost(postId);
        int likesCount = mapper.countLikes(postId); // likes 테이블에서 좋아요 수 계산
        post.setLikesCount(likesCount);
        return post;
    }



//    @Transactional(readOnly = true)
//    public int getLikesCount(Long postId) {
//        return mapper.countLikes(postId); // likes 테이블에서 좋아요 수 계산
//    }

    public List<BoardPost> mySelectPostList(String memberId) {
        List<BoardPost> boardList = mapper.mySelectPostList(memberId);
        return boardList;
    }

    public List<BoardReply> selectReplyByBno(long postId) {
        List<BoardReply> replyList = mapper.selectReplyByBno(postId);
        return replyList;
    }

    public BoardReply createReply(long postId, BoardReply reply, Member member) {
        // reply 객체에 postId, memberId, mno, createDate, modifyDate를 설정
        reply.setPostId(postId);
        reply.setMemberId(member.getId());
        reply.setMno(member.getMno());

        // 현재 시간을 설정 (java.time.LocalDateTime 사용 예)
        LocalDateTime now = LocalDateTime.now();
        reply.setCreateDate(now);
        reply.setModifyDate(now);

        // 댓글을 데이터베이스에 삽입
        mapper.insertReply(reply); // 이 시점에서 reply.rno가 자동으로 설정됨

        return reply; // 생성된 댓글 객체 반환
    }





}
