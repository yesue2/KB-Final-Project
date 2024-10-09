package com.kb.board.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class BoardDTO {
    private long boardId;        // 게시물 번호
    private String type;         // 타입
    private String title;        // 제목
    private String content;      // 내용
    private String status;       // 상태 ('y' 또는 'n')

    public BoardStatus getStatus() {
        return BoardStatus.fromValue(status);
    }

    public BoardPost toBoard() {
        return BoardPost.builder()
                .boardId(boardId)
                .type(type)
                .title(title)
                .content(content)
                .status(getStatus()) // Enum으로 변환된 값
                .build();
    }
}
