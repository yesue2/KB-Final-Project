<script setup>
import { ref, onMounted, computed } from "vue";
import axios from "axios";
import { useAuthStore } from "@/stores/auth";
import api from '@/api/boardApi';

const postType = ref(2); // 게시판 타입 (예: 2: 안정형)
const posts = ref([]); // 게시글 목록
const visibleCount = ref(5); // 보여질 게시글 수
const newReply = ref({}); // 댓글 입력을 위한 ref
const postRefs = ref([]); // hidden input 참조 배열
const replies = ref({}); // 각 게시글의 댓글을 저장할 객체
const editPost = ref({}); // 수정할 게시글을 위한 ref
const isModalOpen = ref(false); // 모달 열림 상태를 위한 ref

const props = defineProps({ username: String });

const avatar = `/api/member/${props.username}/avatar`;
const auth = useAuthStore();

// 게시글 목록을 가져오는 함수
const fetchReplies = async (postId) => {
  try {
    const response = await api.getReplies(postId);
    replies.value[postId] = response;
  } catch (error) {
    console.error("Error fetching replies:", error);
  }
};

// 게시글 목록을 가져오는 함수
const fetchBoardPosts = async () => {
  try {
    const response = await axios.get(`/api/board/${postType.value}/posts`);
    posts.value = response.data.postList;
    
    for (const post of posts.value) {
      await fetchReplies(post.postId);
    }

    console.log("Fetched posts:", posts.value);
  } catch (error) {
    console.error("Error fetching posts:", error);
  }
};

// 보여질 게시글 계산
const visiblePosts = computed(() => {
  return posts.value.slice(0, visibleCount.value);
});

// 모든 게시글이 로드되었는지 확인
const allPostsLoaded = computed(() => {
  return visibleCount.value >= posts.value.length;
});

// 타임스탬프를 형식화하는 함수
const formatDate = (timestamp) => {
  const date = new Date(timestamp);
  return date.toLocaleString("ko-KR", {
    year: "numeric",
    month: "long",
    day: "numeric",
    hour: "numeric",
    minute: "numeric",
    hour12: true,
  });
};

// 더 많은 게시글 로드하는 함수
const loadMore = () => {
  visibleCount.value += 5;
};

// 좋아요 처리
const handleLike = async (index) => {
  const postId = posts.value[index].postId;
  if (postId) {
    try {
      await api.likePost(postId);
    } catch (error) {
      console.error("Failed to like the post:", error);
    }
  }
};

// 게시글 삭제
const handleDelete = async (index) => {
  if (!confirm('삭제할까요?')) return;

  await api.delete(posts.value[index].postId);
  await fetchBoardPosts(); // 게시글을 다시 불러오는 함수 호출
};

// 수정 모달 열기
const openEditModal = (index) => {
  editPost.value = { ...posts.value[index] }; // 선택된 게시물 데이터 복사
  isModalOpen.value = true;
  console.log('Modal opened with post:', editPost.value); // 추가된 로그
};

// 수정 모달 닫기
const closeEditModal = () => {
  isModalOpen.value = false;
};

// 게시글 업데이트
const updatePost = async () => {
  const postId = editPost.value.postId; // 선택된 게시물의 ID 가져오기
  
  const updatedData = {
    bno: editPost.value.bno,
    type: editPost.value.type,
    title: editPost.value.title,
    content: editPost.value.content,
    status: editPost.value.status,
  };

  let token = '';
  const authData = localStorage.getItem('auth');
  if (authData) {
    const parsedAuth = JSON.parse(authData);
    token = parsedAuth.token;
  }

  try {
    // Define response by awaiting the API call
    const response = await axios.put(`/api/board/${postId}`, updatedData, {
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
    });
    
    console.log('Post updated:', response.data); // Correctly reference response.data
    await fetchBoardPosts(); // 게시물 목록 다시 가져오기
    closeEditModal(); // 모달 닫기
  } catch (error) {
    console.error('Error updating post:', error);
    if (error.response) {
      console.error('Server responded with:', error.response.data);
    }
    alert('게시물 수정 중 오류가 발생했습니다. 다시 시도해주세요.');
  }
};


// 컴포넌트가 마운트될 때 게시글 목록을 가져옴
onMounted(() => {
  fetchBoardPosts();
});
</script>

<template>
  <div class="bc">
    <div
        v-for="(post, index) in visiblePosts"
        :key="post.postId"
        class="feeds card mb-5 mb-xxl-8"
    >
      <div class="card-body pb-0">
        <div class="d-flex align-items-center">
          <div class="symbol symbol-45px me-5">
            <img :src="avatar" class="avatar avatar-sm" /><br/>
          </div>
          <div class="d-flex flex-column">
            <p class="name text-gray-800 mb-1 fw-bolder">{{ post.authorId }}</p>
            <span class="text-gray-500 fw-semibold">
              {{ formatDate(post.createdDate) }}
            </span>
          </div>
        </div>
        <div class="pt-5">
          <template v-if="auth.id === post.authorId"> <!-- ===로 변경 -->
            <div class="optionBtn text-end">
              <button
                  type="button"
                  class="btn btn-secondary btn-icon"
                  aria-label="Edit"
                  @click="openEditModal(index)"
              >
                <i class="ai-edit-alt"></i>
              </button>

              <button
                  type="button"
                  class="btn btn-outline-danger btn-icon"
                  aria-label="Delete"
                  @click="handleDelete(index)"
              >
                <i class="ai-trash"></i>
              </button>
            </div>
          </template>
          <h3 class="fw-normal subject mb-3">{{ post.title }}</h3>
          <h5 class="fw-normal content mb-3">{{ post.content }}</h5>
          <div v-if="post.boardAttachFileList && post.boardAttachFileList.length > 0">
            <div v-for="attachment in post.boardAttachFileList" :key="attachment.postId" class="mb-3">
              <img
                  :src="`/api/board/file/${encodeURIComponent(attachment.renamedFilename)}`"
                  alt="Post Image"
                  class="post-image"
              />
            </div>
          </div>
          <input type="hidden" :value="post.postId" :ref="postRefs">
          <div class="d-flex align-items-center">
            <a class="btn btn-sm btn-color-muted btn-active-light-primary fw-bolder fs-6 py-1 px-2 me-4">
              <i class="ai-message fs-2"></i>{{ post.commentCount }}
            </a>
            <a
                class="btn btn-sm btn-color-muted btn-active-light-danger fw-bold fs-6 py-1 px-2"
                @click="handleLike(index)"
            >
              <i class="ai-heart fs-2"></i>{{ post.likesCount }}
            </a>
          </div>
        </div>
        <div class="separator pt-5 mb-3"></div>
        <!-- 댓글 목록 표시 -->
        <div class="replies">
          <div v-for="reply in replies[post.postId] || []" :key="reply.rno" class="reply">
            <h5>{{ reply.content }}</h5>
            <h5>{{ formatDate(reply.createDate) }}</h5>
          </div>
        </div>
        <!-- 댓글 입력란 -->
        <form class="reply position-relative pb-3" @submit.prevent="handleReply(index)">
          <input
              v-model="newReply[index]"
              data-kt-autosize="true"
              class="form-control border-0 p-0 pe-10 resize-none min-h-25px"
              placeholder="댓글"
          />
          <div class="position-absolute top-0 end-0 me-n5">
            <button class="btn btn-icon btn-sm btn-active-color-primary ps-0" type="submit">
              <i class="ai-edit-alt"></i>
            </button>
          </div>
        </form>
      </div>
    </div>
    
    <!-- 수정 모달 -->
    <div v-if="isModalOpen" class="modal">
      <div class="modal-content">
        <span @click="closeEditModal" class="close">&times;</span>
        <h2>게시물 수정</h2>
        <input v-model="editPost.title" placeholder="제목" />
        <textarea v-model="editPost.content" placeholder="내용"></textarea>
        <button @click="updatePost">수정 완료</button>
      </div>
    </div>

    <button v-if="!allPostsLoaded" @click="loadMore" class="btn btn-primary">더보기</button>
  </div>
</template>

<style scoped>
.bc {
  font-family: J3;
}
.reply {
  font-size: 20px;
}

.reply textarea {
  font-size: 20px;
}

.pt-5 p {
  font-size: 25px;
}

.name {
  font-size: 20px;
}

.moreBtn {
  margin-left: 10px;
  width: 700px;
  height: 50px;
  border: none;
  border-radius: 20px;
  cursor: pointer; /* Add cursor pointer for better UX */
}

.moreBtn:hover {
  background-color: #438c74;
  color: white;
}

.feeds {
  width: 700px;
  border-radius: 30px;
  border: none;
  margin-left: 10px;
}

.post-image {
  max-width: 600px;
  max-height: 600px;
}

.optionBtn {
  cursor: pointer;
  margin-top: -100px;
}

.subject {
  margin-top: 50px;
}

.content {
  margin-top: -20px;
}

.btn {
  background-color: white;
}

.replies {
  margin-top: 20px; /* 댓글 목록 상단 여백 추가 */
}

.reply {
  display: flex;
  justify-content: space-between;
  padding: 10px; /* 댓글 여백 추가 */
  border-bottom: 1px solid lightgrey;
}

.modal {
  display: flex;
  justify-content: center;
  align-items: center;
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: rgba(0, 0, 0, 0.7); /* 흐릿한 배경 */
  z-index: 1000; /* 다른 요소들 위에 위치하도록 설정 */
}

.modal-content {
  background-color: rgba(67, 140, 116, 1); 
  padding: 20px;
  border-radius: 15px; /* 모서리 둥글게 */
  width: 450px; /* 폭 */
  max-width: 90%; /* 모바일 대응 */
  position: relative;
  
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15); /* 그림자 효과 */
  transition: transform 0.3s ease, opacity 0.3s ease; /* 부드러운 애니메이션 효과 */
}


.modal-content>h2 {
  color : #fff;
}

.modal-content>button {
  color: #fff;
  background-color: darkgreen; /* 흐릿한 배경 */
}

.modal-header {
  font-size: 24px; /* 제목 글자 크기 */
  margin-bottom: 15px; /* 제목 아래 여백 */
  text-align: center; /* 가운데 정렬 */
  color: #444; /* 제목 색상 */
}

.modal-body {
  margin-bottom: 20px; /* 내용 아래 여백 */
  font-size: 16px; /* 내용 글자 크기 */
  line-height: 1.5; /* 줄 간격 조정 */
}

.modal-footer {
  display: flex;
  justify-content: space-between; /* 버튼 간격 조정 */
}

.modal-button {
  background-color: #438c74; /* 버튼 배경색 */
  color: #fff; /* 버튼 글자색 */
  border: none;
  border-radius: 5px; /* 모서리 둥글게 */
  padding: 10px 15px; /* 패딩 조정 */
  cursor: pointer;
  transition: background-color 0.3s ease; /* 부드러운 배경색 변경 */
  font-size: 16px; /* 버튼 글자 크기 */
}

.modal-button:hover {
  background-color: #367b62; /* 마우스 오버 시 색상 변경 */
}

.close {
  position: absolute;
  top: 10px;
  right: 10px;
  cursor: pointer;
  font-size: 18px; /* 글자 크기 */
  color: #999; /* 글자 색상 */
}

.close:hover {
  color: #333; /* 마우스 오버 시 색상 변경 */
}

</style>
