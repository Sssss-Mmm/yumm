package com.example.demo.dto.users;

/**
 * 비밀번호 정책(FR-A-09): 8자 이상, 영문·숫자 조합.
 *
 * 가입과 비밀번호 변경이 같은 제약을 써야 한다. 정규식을 각 DTO에 복사해 두면 한쪽만 고쳐져
 * "가입은 막히는데 변경으로는 통과하는" 비밀번호가 생긴다. @Pattern은 상수만 받으므로 여기 모아 둔다.
 */
public final class PasswordPolicy {

    /** 상한 64는 BCrypt가 72바이트 뒤를 잘라 버리기 때문에 둔다. */
    public static final String PATTERN = "^(?=.*[A-Za-z])(?=.*\\d)\\S{8,64}$";

    public static final String MESSAGE = "비밀번호는 8자 이상이며 영문과 숫자를 모두 포함해야 합니다.";

    private PasswordPolicy() {
    }
}
