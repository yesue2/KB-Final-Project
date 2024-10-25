<script setup>
import { ref, onMounted, reactive } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import WriterPopup from './WriterPopup.vue';
import axios from 'axios';

const propensityTypes = {
  stability: {
    label: '안정형',
    route: '/community/stability',
    types: ['IPWC', 'IPMC', 'IBWC', 'IBMC']
  },
  neutral: {
    label: '중립형',
    route: '/community/neutral',
    types: ['IPML', 'IPWL', 'IBML', 'IBWL']
  },
  activeInvestment: {
    label: '적극투자형',
    route: '/community/activeInvestment',
    types: ['APWL', 'APML', 'ABWC', 'APMC']
  },
  aggressiveInvestment: {
    label: '공격투자형',
    route: '/community/aggressiveInvestment',
    types: ['ABWL', 'APWC', 'ABMC', 'ABML']
  }
};

const props = defineProps({ username: String });

const avatar = `/api/member/${props.username}/avatar`;

// 상태 관리
const activePropensity = ref('');
const activeGroup = ref(null); // 현재 활성화된 그룹
const showModal = ref(false);
const userPropensity = ref('');
const loading = ref(false);
const error = ref(null);

const router = useRouter();
const route = useRoute();

const myInfo = reactive({
  name: '',
  id: '',
  email: '',
  investType: '', // API 응답의 investType 사용
});

// 사용자 정보 가져오기 (API 호출)
async function getMyInfo() {
  const authValue = localStorage.getItem('auth');
  
  if (authValue) {
    try {
      const authData = JSON.parse(authValue);
      if (authData.id) {
        myInfo.id = authData.id;
        myInfo.email = authData.email;

        if (authData.investType) {
          myInfo.investType = authData.investType.toString();
          userPropensity.value = authData.investType.toString();

          // 성향에 맞춰 활성화 설정
          setActivePropensity(myInfo.investType);
        } else {
          console.warn('investType이 auth 데이터에 없습니다.');
        }

        loading.value = true;
        const response = await axios.get(`/api/member/${myInfo.id}`);
        const data = response.data;
        myInfo.value = response.data;

        if (data) {
          myInfo.name = data.name;
        } else {
          console.error('API 응답이 비어 있습니다.');
        }
      }
    } catch (err) {
      error.value = '사용자 정보를 가져오는 중 오류가 발생했습니다.';
    } finally {
      loading.value = false;
    }
  } else {
    error.value = '로그인이 필요합니다.';
  }
}

// Propensity 설정 및 라우팅
function setActivePropensity(propensity) {
  const foundType = Object.keys(propensityTypes).find(group =>
    propensityTypes[group].types.includes(propensity)
  );

  if (foundType) {
    activePropensity.value = propensity;

    const targetRoute = propensityTypes[foundType].route;
    if (route.path !== targetRoute) {
      router.push(targetRoute);
    }
    
    // 그룹 활성화 상태 설정
    activeGroup.value = foundType; // 수정된 부분
  } else {
    console.error('유효하지 않은 유형:', propensity);
  }
}

// 팝업 제어
function openPopup() {
  showModal.value = true;
}

function closePopup() {
  showModal.value = false;
}

onMounted(async () => {
  await getMyInfo();
  if (myInfo.investType) {
    console.log("Test" + myInfo.investType);
    setActivePropensity(myInfo.investType);
  }
});
</script>

<template>
  <div class="communityPanel d-inline-block text-start">
    <!-- 로딩 상태 표시 -->
    <div v-if="loading" class="loading">로딩 중...</div>

    <!-- 에러 메시지 표시 -->
    <div v-if="error" class="error-message">{{ error }}</div>

    <!-- 로딩이 완료되고 에러가 없을 때 콘텐츠 표시 -->
    <div v-else>
      <div class="profile">
        <img :src="avatar" class="avatar avatar-sm" /><br/>
        <h2 class="d-inline">{{ myInfo.id }}</h2>
        <h2 style="font-weight: 100;" class="d-inline">님</h2>
        <h4 style="font-weight: lighter;">{{ myInfo.email }}</h4>
      </div><br/>

      <!-- 성향 그룹 표시 -->
      <div v-for="(group, key) in propensityTypes" :key="key">
        <div
          class="propensity"
          @click="setActivePropensity(group.types[0])"
          :class="{ active: activeGroup === key }"
        >
          <h4>{{ group.label }}</h4>
        </div>
      </div>

      <div
          class="myPage"
          @click="router.push('/mypage')"
      >
        <hr>
        <br>
        <h4>마이페이지</h4>
      </div>

      <div>
        <button
            class="writerBtn"
            @click="openPopup"
            :disabled="activePropensity !== userPropensity"
        >
          새 글 작성하기
        </button>
      </div>
      <br/><br/>
    </div>
  </div>
  <br><br>
  <div class="warnSign">
    <h6>
      커뮤니티는 게시판 제공만 하고 있습니다<br>
      서비스는
      <a href="/communityPrivacy" class="d-inline"> 커뮤니티정책</a>에 따라 운영됩니다
    </h6>
  </div>

  <!-- WriterPopup 컴포넌트 (모달) -->
  <WriterPopup v-if="showModal" @close="closePopup"/>

  <!-- 흐림 배경 -->
  <div v-if="showModal" class="overlay" @click="closePopup"></div>
</template>

<style scoped>
.warnSign h6 {
  color: #4c4c4c;
}

a {
  color: #0f9d58;
}

.writerBtn {
  width: 100%; /* 버튼 너비를 100%로 변경하여 패널에 맞춤 */
  height: 40px;
  border: none;
  border-radius: 20px;
  font-size: 18px; /* 폰트 크기 조정 */
  color: white;
  background-color: rgba(67, 140, 116, 1);
}

.overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background-color: rgba(0, 0, 0, 0.5);
  z-index: 10;
}

.writerBtn:disabled {
  background-color: rgba(225, 225, 225, 0.5);
  color: #8f8f8f;
}

.writerBtn:hover {
  background-color: rgba(67, 140, 116, 0.31);
  color: rgba(67, 140, 116, 1);
}

.propensity {
  width: 100%; /* 성향 버튼 너비를 100%로 변경 */
  padding: 10px;
  cursor: pointer;
  margin: 5px 0; /* 수직 마진 조정 */
}

.myPage {
  width: 100%; /* 마이페이지 버튼 너비를 100%로 변경 */
  padding: 10px;
  cursor: pointer;
  margin: 5px 0; /* 수직 마진 조정 */
}

.propensity:hover {
  height: 50px;
  border: 1px solid rgba(67, 140, 116, 1);
  border-radius: 20px;
  padding: 10px;
}

.propensity.active {
  height: 50px;
  border: 1px solid rgba(67, 140, 116, 1);
  border-radius: 20px;
  color: rgba(67, 140, 116, 1);
}

.myPage:hover {
  border-radius: 20px;
}

.communityPanel {
  font-family: J3;
  width: 250px; /* 크기 조정 */
  height: auto; /* 자동 높이 조정 */
  border-radius: 30px;
  padding: 20px; /* 패딩 조정 */
  background-color: white;
}

.profile {
  padding: 20px; /* 패딩 조정 */
  margin-top: -20px; /* 여백 조정 */
}

.profile img {
  width: 70px; /* 이미지 크기 조정 */
  border-radius: 50%;
}
</style>

