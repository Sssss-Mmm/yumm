// console-client/cli.js

const axios = require('axios');
const readline = require('readline');
const { STATES, EVENTS, TRANSITIONS } = require('./stateMachine');
const fs = require('fs');

const TOKEN_FILE = './tokens.json';

// 401 인터셉터를 위한 axios 인스턴스 생성
const client = axios.create(); // client 인스턴스는 이제 기본 Authorization 헤더를 가질 것입니다.

let tokens = { accessToken: null, refreshToken: null };

function loadTokens() {
    try {
        if (fs.existsSync(TOKEN_FILE)) {
            const data = fs.readFileSync(TOKEN_FILE, 'utf8');
            tokens = JSON.parse(data);
            console.log('✅ 토큰 로드 완료.');
        }
    } catch (err) {
        console.error('❌ 토큰 로드 중 오류:', err.message);
    }
}

function saveTokens() {
    try {
        fs.writeFileSync(TOKEN_FILE, JSON.stringify(tokens, null, 2), 'utf8');
        console.log('✅ 토큰 저장 완료.');
    } catch (err) {
        console.error('❌ 토큰 저장 중 오류:', err.message);
    }
}

function clearTokens() {
    tokens = { accessToken: null, refreshToken: null };
    if (fs.existsSync(TOKEN_FILE)) {
        fs.unlinkSync(TOKEN_FILE);
        console.log('🗑️ 토큰 파일 삭제 완료.');
    }
    // 🟢 중요: 토큰 삭제 시 client 인스턴스의 기본 Authorization 헤더도 제거
    delete client.defaults.headers.common['Authorization'];
}

// 401 응답 인터셉터 설정: Access Token 만료 시 Refresh Token으로 재발급 시도
client.interceptors.response.use(
    response => {
        console.log(`✅ [RESPONSE] ${response.status} ${response.config.url}`);
        return response;
    },
    async error => {
        const { config, response } = error;
        console.log(`❌ [RESPONSE] ${response?.status} ${config.url}`);

        const isRefreshAttempt = config.url && config.url.includes(API.refresh);
        if (response?.status === 401 && !config._retry && !isRefreshAttempt) {
            config._retry = true;
            console.log('액세스 토큰 만료 또는 유효하지 않음. 재발급 시도...');
            try {
                await doRefresh(); // 액세스 토큰 재발급 함수 호출
                // 새로 발급받은 Access Token은 client.defaults.headers.common['Authorization']에 이미 설정됨.
                // 따라서 원본 config의 Authorization 헤더는 직접 수정할 필요 없음.
                // axios는 retry 시 client.defaults.headers.common를 자동으로 사용합니다.
                return client(config); // 원본 요청 재시도
            } catch (refreshError) {
                console.error('❌ 액세스 토큰 재발급 실패. 다시 로그인하세요.');
                state = STATES.UNAUTHENTICATED;
                clearTokens();
                return Promise.reject(refreshError);
            }
        }
        return Promise.reject(error);
    }
);

const API = {
    signup: 'http://localhost:8080/api/user/signup',
    login: 'http://localhost:8080/api/auth/login',
    getProfile: 'http://localhost:8080/api/user/profile',
    updateProfile: 'http://localhost:8080/api/user/profile',
    updateEmail: 'http://localhost:8080/api/user/email',
    updatePhoneNumber: 'http://localhost:8080/api/user/phone-number',
    getUserDetails: 'http://localhost:8080/api/user/me/details',
    changePW: 'http://localhost:8080/api/user/password',
    logout: 'http://localhost:8080/api/auth/logout',
    withdraw: 'http://localhost:8080/api/user/withdraw',
    refresh: 'http://localhost:8080/api/auth/refresh',
};

const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
const question = q => new Promise(res => rl.question(q, res));

let state;

async function run() {
    console.log('=== 콘솔 클라이언트 시작 ===');
    loadTokens();

    while (true) {
        if (tokens.accessToken && tokens.refreshToken) {
            console.log('\n🔍 기존 토큰 유효성 확인 중...');
            try {
                // 기존 토큰 유효성 검증: client.defaults.headers.common 사용
                client.defaults.headers.common['Authorization'] = `Bearer ${tokens.accessToken}`; // 초기 Access Token 설정
                await client.get(API.getProfile); // headers 옵션 제거
                state = STATES.AUTHENTICATED;
                console.log(`✅ 기존 세션 유지됨 → 상태: ${state}`);
            } catch (err) {
                console.log('⚠️ 기존 토큰 만료 또는 유효하지 않음. 다시 로그인하세요.');
                state = STATES.UNAUTHENTICATED;
                clearTokens(); // 유효하지 않은 토큰 삭제 (내부에서 기본 헤더도 제거)
            }
        } else {
            state = STATES.UNAUTHENTICATED;
            console.log('✨ 새 세션 시작 → 상태: UNAUTHENTICATED');
        }

        let endSession = false;
        while (!endSession) {
            console.log(`\n[${state.toUpperCase()}] 가능한 액션:`);
            let menu = [];

            if (state === STATES.UNAUTHENTICATED) {
                menu = [
                    { num: '1', label: '회원가입', event: EVENTS.SIGNUP },
                    { num: '2', label: '로그인', event: EVENTS.LOGIN }
                ];
            } else if (state === STATES.AUTHENTICATED) {
                menu = [
                    { num: '1', label: '프로필 조회 (닉네임/URL)', event: EVENTS.GET_PROFILE },
                    { num: '2', label: '프로필 수정 (닉네임/URL)', event: EVENTS.UPDATE_PROFILE },
                    { num: '3', label: '이메일 변경', event: EVENTS.UPDATE_EMAIL },
                    { num: '4', label: '전화번호 변경', event: EVENTS.UPDATE_PHONE_NUMBER },
                    { num: '5', label: '상세 정보 조회 (이메일/전화번호 포함)', event: EVENTS.GET_USER_DETAILS },
                    { num: '6', label: '비밀번호 변경', event: EVENTS.CHANGE_PW },
                    { num: '7', label: '로그아웃', event: EVENTS.LOGOUT },
                    { num: '8', label: '회원탈퇴', event: EVENTS.WITHDRAW }
                ];
            }
            
            console.log(`${menu.map(item => `${item.num}) ${item.label}`).join('\n')}`);
            const choice = (await question('> ')).trim();
            const selected = menu.find(item => item.num === choice);

            if (!selected) {
                console.log('🚫 올바른 번호를 입력하세요.');
                continue;
            }
            const action = selected.event;

            try {
                switch (action) {
                    case EVENTS.SIGNUP: await doSignup(); break;
                    case EVENTS.LOGIN: await doLogin(); break;
                    case EVENTS.GET_PROFILE: await doGetProfile(); break;
                    case EVENTS.UPDATE_PROFILE: await doUpdateProfile(); break;
                    case EVENTS.UPDATE_EMAIL: await doUpdateEmail(); break;
                    case EVENTS.UPDATE_PHONE_NUMBER: await doUpdatePhoneNumber(); break;
                    case EVENTS.GET_USER_DETAILS: await doUserDetails(); break;
                    case EVENTS.CHANGE_PW: await doChangePW(); break;
                    case EVENTS.LOGOUT: await doLogout(); break;
                    case EVENTS.WITHDRAW: await doWithdraw(); break;
                }

                const nextState = TRANSITIONS[state] ? TRANSITIONS[state][action] : state;
                if (nextState) {
                    state = nextState;
                    console.log(`➡️ ${selected.label} 성공 → 상태: ${state}`);
                } else {
                    console.log(`➡️ ${selected.label} 성공 (상태 변화 없음: ${state})`);
                }

                if (action === EVENTS.LOGOUT || action === EVENTS.WITHDRAW) {
                    console.log(`\n${selected.label} 완료. 메인 메뉴로 돌아갑니다.`);
                    endSession = true;
                }
            } catch (err) {
                console.error('❌ 에러:', err.response?.data?.message || err.message || '알 수 없는 오류 발생');
                if (err.response?.status === 401) {
                    console.log('토큰이 유효하지 않습니다. 다시 로그인해주세요.');
                    state = STATES.UNAUTHENTICATED;
                    clearTokens();
                }
            }
        }
    }
}

// --- 헬퍼 함수들 (API 호출 로직) ---

/** 회원가입: 사용자 정보를 입력받아 POST /api/user/signup 호출 */
async function doSignup() {
    const email = await question('이메일 (로그인ID): ');
    const password = await question('비밀번호: ');
    const nickname = await question('닉네임: ');
    const gender = (await question('성별 (MALE/FEMALE): ')).toUpperCase();
    const age = parseInt(await question('나이: '));
    const phoneNumber = await question('전화번호 (010xxxxxxxx): ');
    const profileImageUrl = await question('프로필 이미지 URL (선택, 없으면 엔터): ');

    await axios.post(
        API.signup,
        { email, password, nickname, gender, age, phoneNumber, profileImageUrl: profileImageUrl || null }
    );
    console.log('✅ 회원가입 완료');
}

/** 로그인: email/password를 입력받아 POST /api/auth/login 호출 후 토큰 저장 */
async function doLogin() {
    const email = await question('이메일 (로그인ID): ');
    const password = await question('비밀번호: ');
    const res = await axios.post(API.login, {
        email,
        password
    });

    tokens.accessToken = res.data.data.accessToken;
    tokens.refreshToken = res.data.data.refreshToken;
    saveTokens();
    // 🟢 중요: 로그인 성공 시 client 인스턴스의 기본 Authorization 헤더 설정
    client.defaults.headers.common['Authorization'] = `Bearer ${tokens.accessToken}`;
    console.log('✅ 로그인 성공');
}

/** 로그아웃: Refresh Token으로 POST /api/auth/logout 호출 후 로컬 토큰 삭제 */
async function doLogout() {
    if (!tokens.refreshToken) {
        console.log('⚠️ 리프레시 토큰이 없습니다. 이미 로그아웃되었거나 토큰이 발급되지 않았습니다.');
        return;
    }
    // 🟢 중요: axios 기본 인스턴스 사용 (client 대신 axios.post)
    //         서버의 @RequestBody RefreshTokenRequestDto와 일치하도록 본문에 담아 전송
    await axios.post(
        API.logout,
        { refreshToken: tokens.refreshToken } // Refresh Token을 요청 본문에 담아 전송
    );
    clearTokens();
    console.log('✅ 로그아웃 완료');
}

/** 회원 탈퇴: Access Token으로 DELETE /api/user/withdraw 호출 후 로컬 토큰 삭제 */
async function doWithdraw() {
    if (!tokens.accessToken) {
        console.log('⚠️ 액세스 토큰이 없습니다. 로그인 상태가 아닙니다.');
        return;
    }
    await client.delete(API.withdraw); // 🟢 headers 옵션 제거
    clearTokens();
    console.log('✅ 회원탈퇴 완료');
}

/** 프로필 조회 (닉네임, 프로필 이미지 URL만): GET /api/user/profile 호출 */
async function doGetProfile() {
    if (!tokens.accessToken) {
        console.log('⚠️ 액세스 토큰이 없습니다. 로그인 상태가 아닙니다.');
        return;
    }
    const res = await client.get(API.getProfile); // 🟢 headers 옵션 제거
    console.log('🔎 내 프로필 (닉네임, URL):', res.data.data);
}

/** 프로필 수정 (닉네임, 프로필 이미지 URL): PUT /api/user/profile 호출 */
async function doUpdateProfile() {
    if (!tokens.accessToken) {
        console.log('⚠️ 액세스 토큰이 없습니다. 로그인 상태가 아닙니다.');
        return;
    }
    const nickname = await question('새 닉네임: ');
    const profileImageUrl = await question('새 프로필 이미지 URL (선택, 없으면 엔터): ');
    
    const res = await client.put(
        API.updateProfile,
        { nickname, profileImageUrl: profileImageUrl || null }
    ); // 🟢 headers 옵션 제거
    console.log('✅ 프로필 수정 완료:', res.data.data);
}

/** 이메일 변경: PUT /api/user/email 호출 */
async function doUpdateEmail() {
    if (!tokens.accessToken) {
        console.log('⚠️ 액세스 토큰이 없습니다. 로그인 상태가 아닙니다.');
        return;
    }
    const newEmail = await question('새 이메일: ');
    const currentPassword = await question('현재 비밀번호 (본인 인증용): ');

    const res = await client.put(
        API.updateEmail,
        { newEmail, currentPassword }
    ); // 🟢 headers 옵션 제거
    console.log('✅ 이메일 변경 완료:', res.data.data);
    console.log('🔔 이메일 변경으로 인해 세션이 종료될 수 있습니다. 다시 로그인해주세요.');
    state = STATES.UNAUTHENTICATED;
    clearTokens();
}

/** 전화번호 변경: PUT /api/user/phone-number 호출 */
async function doUpdatePhoneNumber() {
    if (!tokens.accessToken) {
        console.log('⚠️ 액세스 토큰이 없습니다. 로그인 상태가 아닙니다.');
        return;
    }
    const newPhoneNumber = await question('새 전화번호 (010xxxxxxxx): ');
    const currentPassword = await question('현재 비밀번호 (본인 인증용): ');

    const res = await client.put(
        API.updatePhoneNumber,
        { newPhoneNumber, currentPassword }
    ); // 🟢 headers 옵션 제거
    console.log('✅ 전화번호 변경 완료:', res.data.data);
}

/** 사용자 상세 정보 조회 (모든 프로필 정보 포함): GET /api/user/me/details 호출 */
async function doUserDetails() {
    if (!tokens.accessToken) {
        console.log('⚠️ 액세스 토큰이 없습니다. 로그인 상태가 아닙니다.');
        return;
    }
    const res = await client.get(API.getUserDetails); // 🟢 headers 옵션 제거
    console.log('🔎 내 상세 정보:', res.data.data);
}

/** 비밀번호 변경: PUT /api/user/password 호출 */
async function doChangePW() {
    if (!tokens.accessToken) {
        console.log('⚠️ 액세스 토큰이 없습니다. 로그인 상태가 아닙니다.');
        return;
    }
    const oldPassword = await question('현재 비밀번호: ');
    const newPassword = await question('새 비밀번호: ');
    await client.put(API.changePW,
        { oldPassword, newPassword }
    ); // 🟢 headers 옵션 제거
    console.log('✅ 비밀번호 변경 완료. 재로그인해주세요.');
    state = STATES.UNAUTHENTICATED;
    clearTokens();
}

/** 액세스 토큰 재발급: Refresh Token으로 POST /api/auth/refresh 호출 */
async function doRefresh() {
    if (!tokens.refreshToken) {
        console.log('⚠️ 리프레시 토큰이 없어 재발급할 수 없습니다. 로그인해주세요.');
        throw new Error('리프레시 토큰 없음.');
    }
    console.log("⟳ 액세스 토큰 재발급 시도 중...");
    // 🟢 중요: axios 기본 인스턴스 사용 (client 대신 axios.post)
    const res = await axios.post(
        API.refresh,
        { refreshToken: tokens.refreshToken } // Refresh Token을 요청 본문에 담아 전송
    );
    tokens.accessToken = res.data.data.accessToken;
    if (res.data.data.refreshToken) { 
        tokens.refreshToken = res.data.data.refreshToken;
    }
    saveTokens();
    // 🟢 중요: 재발급 성공 시 client 인스턴스의 기본 Authorization 헤더 업데이트
    client.defaults.headers.common['Authorization'] = `Bearer ${tokens.accessToken}`;
    console.log('✅ 액세스 토큰 재발급 완료');
}

// 애플리케이션 실행
run();