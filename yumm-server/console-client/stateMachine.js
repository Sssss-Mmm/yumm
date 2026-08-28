// statemachine.js

const STATES = {
    DEFAULT: 'default', // 초기 상태 (예: 역할 선택)
    UNAUTHENTICATED: 'unauthenticated', // 인증되지 않은 상태
    AUTHENTICATED: 'authenticated' // 인증된 상태
};

const EVENTS = {
    // --- 변경된 부분: 역할 선택 이벤트는 이제 cli.js에서 사용되지 않음 (auth/user 분리) ---
    // SELECT_USER:      'selectUser',
    // SELECT_ADMIN:     'selectAdmin',
    // --- 변경된 부분 끝 ---

    SIGNUP:           'signup',
    LOGIN:            'login',

    // --- 변경된 부분: API 엔드포인트 변경 및 추가 반영 ---
    GET_PROFILE:      'getProfile',      // 닉네임/프로필 URL만 조회
    UPDATE_PROFILE:   'updateProfile',   // 닉네임/프로필 URL 수정
    UPDATE_EMAIL:     'updateEmail',     // 이메일 변경 (새로 추가)
    UPDATE_PHONE_NUMBER: 'updatePhoneNumber', // 전화번호 변경 (새로 추가)
    GET_USER_DETAILS: 'getUserDetails',  // 모든 상세 정보 조회 (새로 추가)
    // --- 변경된 부분 끝 ---

    CHANGE_PW:        'changePW',
    WITHDRAW:         'withdraw',
    LOGOUT:           'logout',

    // --- 변경된 부분: 매칭 시스템 제거 반영 ---
    // MATCHING:         'matching'
    // --- 변경된 부분 끝 ---
};

const TRANSITIONS = {
    // --- 변경된 부분: DEFAULT 상태의 전이 로직 변경 ---
    // cli.js에서 'actor' 선택이 없어졌으므로, DEFAULT 상태는 직접 UNAUTHENTICATED로 시작할 수 있도록 변경
    [STATES.DEFAULT]: {
        // 클라이언트가 처음 시작하거나, 세션이 끝나면 DEFAULT 상태로 와서
        // UNAUTHENTICATED 또는 AUTHENTICATED로 바로 전이될 수 있음 (cli.js 로직에서 처리)
        // 여기서는 명시적인 상태 전이는 제거하고, cli.js에서 상태를 직접 설정
    },
    // --- 변경된 부분 끝 ---

    [STATES.UNAUTHENTICATED]: {
        [EVENTS.SIGNUP]: STATES.UNAUTHENTICATED, // 회원가입 후 바로 인증 상태로 가지 않음 (cli.js에서 로그인 유도)
        [EVENTS.LOGIN]: STATES.AUTHENTICATED
    },

    [STATES.AUTHENTICATED]: {
        // --- 변경된 부분: API 엔드포인트 변경 및 추가 반영 ---
        [EVENTS.GET_PROFILE]: STATES.AUTHENTICATED,
        [EVENTS.UPDATE_PROFILE]: STATES.AUTHENTICATED,
        [EVENTS.UPDATE_EMAIL]: STATES.UNAUTHENTICATED, // 이메일 변경 시 토큰 무효화 가능성 있으므로 UNAUTHENTICATED로 전이
        [EVENTS.UPDATE_PHONE_NUMBER]: STATES.AUTHENTICATED,
        [EVENTS.GET_USER_DETAILS]: STATES.AUTHENTICATED,
        // --- 변경된 부분 끝 ---

        [EVENTS.CHANGE_PW]: STATES.UNAUTHENTICATED, // 비밀번호 변경 시 토큰 무효화 가능성 있으므로 UNAUTHENTICATED로 전이
        [EVENTS.WITHDRAW]: STATES.UNAUTHENTICATED,
        [EVENTS.LOGOUT]: STATES.UNAUTHENTICATED, // 로그아웃 시 다시 비인증 상태로 (default 대신)

        // --- 변경된 부분: 매칭 시스템 제거 반영 ---
        // [EVENTS.MATCHING]: STATES.AUTHENTICATED
        // --- 변경된 부분 끝 ---
    }
};

module.exports = {
    STATES,
    EVENTS,
    TRANSITIONS
};