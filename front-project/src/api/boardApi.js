import api from '@/api'; // 인터셉터 사용 (index.js)

const BASE_URL = '/api/board';
const headers = { 'Content-Type': 'multipart/form-data' }; // 파일업로드 하려고

export default {
    async getList(params) {
        // url과 params 매개변수로 서버에 요청을 보내고 data를 받아온다
        const { data } = await api.get(BASE_URL, { params });
        console.log('BOARD GET LIST: ', data);
        return data;
    },


    // async createReply(postId, replyDTO) {
    //     const { data } = await api.get(`/api/board/replyPlus/${postId}`, replyDTO);
    //     return data;
    // },

    likePost(postId) {
        return api.post(`/api/board/${postId}/like`, {}, {
          headers: {
            Authorization: `Bearer ${localStorage.getItem('authToken')}`, // 적절한 토큰 설정
          },
        });
      },

    async getReplies(postId) {
        const { data } = await api.get(`${BASE_URL}/reply/${postId}`);
        
        // 날짜 배열을 Date 객체로 변환
        data.forEach(reply => {
            reply.createDate = new Date(...reply.createDate);
            reply.modifyDate = new Date(...reply.modifyDate);
        });
    
        return data;
    },
    
    
    async addReply(postId, replyData){
        return await api.post(`/api/board/replyPlus/${postId}`, replyData);
    },


    async create(article) {
        const formData = new FormData();
        formData.append('title', article.title);
        formData.append('memberId', article.memberId);
        formData.append('content', article.content);

        // 파일이 존재하는 경우 해당 파일의 길이만큼 돌면서 각 파일을 formData에 추가
        if (article.files) {
            for (let i = 0; i < article.files.length; i++) {
                formData.append('files', article.files[i]);
            }
        }
        // API로 폼 데이터를 함께 담아서 post요청
        const { data } = await api.post(BASE_URL, formData, { headers });
        console.log('BOARD POST: ', data);
        return data;
    },
    // async get(no) {
    //     const { data } = await api.get(`${BASE_URL}/${no}`);
    //     console.log('BOARD GET', data);
    //     return data;
    // },
    async delete(postId) {
        const { data } = await api.delete(`${BASE_URL}/${postId}`);
        console.log('BOARD DELETE: ', data);
        return data;
    },

    // 게시글 수정
    async update(article) {
        const formData = new FormData();
        formData.append('no', article.no);
        formData.append('title', article.title);
        formData.append('memberId', article.memberId);
        formData.append('content', article.content);
        if (article.files) { // 첨부파일이 있는 경우
            for (let i = 0; i < article.files.length; i++) {
                formData.append('files', article.files[i]);
            }
        }
        const { data } = await api.put(`${BASE_URL}/${article.no}`, article, { headers });
        console.log('BOARD PUT: ', data);
        return data;
    },

    // 첨부파일 삭제
    async deleteAttachment(no) {
        const { data } = await api.delete(`${BASE_URL}/deleteAttachment/${no}`);
        console.log('ATTACHMENT DELETE: ', data);
        return data;
    },

}