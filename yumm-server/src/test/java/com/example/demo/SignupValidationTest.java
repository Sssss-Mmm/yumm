package com.example.demo;

import com.example.demo.dto.users.SignupRequest;
import com.example.demo.exception.ErrorCode;
import com.example.demo.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 가입 요청 검증(FR-A-01 / FR-A-09).
 *
 * 제약이 없으면 필드 누락이 NPE·BCrypt 예외·NOT NULL 위반으로 500까지 흘러가고,
 * 형식이 틀린 이메일은 인증 코드를 받을 수 없는 계정이 되어 주소만 점유한다.
 * 컨트롤러가 실제로 받는 경로와 같게 JSON을 역직렬화한 뒤 검증한다.
 */
class SignupValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String VALID = """
            {"email":"me@yumm.local","password":"yumm1234","nickname":"먹보",
             "gender":"MALE","birthYear":1995,"agreedToTerms":true}
            """;

    /** 위반한 필드 이름들. 비어 있으면 400 없이 통과한다는 뜻이다. */
    private static Set<String> violatedFields(String json) throws Exception {
        SignupRequest request = MAPPER.readValue(json, SignupRequest.class);
        return VALIDATOR.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("모든 항목을 갖춘 요청은 통과한다")
    void validRequestPasses() throws Exception {
        assertThat(violatedFields(VALID)).isEmpty();
    }

    @Test
    @DisplayName("email·password·nickname·gender가 빠지면 400이다 (500이 아니라)")
    void missingFieldsAreRejected() throws Exception {
        assertThat(violatedFields("""
                {"birthYear":1995,"agreedToTerms":true}
                """)).containsExactlyInAnyOrder("email", "password", "nickname", "gender");

        // 공백만 넣는 우회도 같이 막힌다
        assertThat(violatedFields("""
                {"email":" ","password":" ","nickname":" ","gender":" ",
                 "birthYear":1995,"agreedToTerms":true}
                """)).contains("email", "password", "nickname", "gender");
    }

    @Test
    @DisplayName("이메일 형식이 아니면 거부한다")
    void invalidEmailFormatRejected() throws Exception {
        assertThat(violatedFields("""
                {"email":"not-an-email","password":"yumm1234","nickname":"먹보",
                 "gender":"MALE","birthYear":1995,"agreedToTerms":true}
                """)).containsExactly("email");
    }

    @Test
    @DisplayName("비밀번호는 8자 이상 영문·숫자 조합이어야 한다 (FR-A-09)")
    void passwordPolicy() throws Exception {
        // 8자 미만 / 숫자 없음 / 영문 없음
        assertThat(violatedFields(VALID.replace("yumm1234", "yumm12"))).containsExactly("password");
        assertThat(violatedFields(VALID.replace("yumm1234", "yummyummy"))).containsExactly("password");
        assertThat(violatedFields(VALID.replace("yumm1234", "12345678"))).containsExactly("password");
        assertThat(violatedFields(VALID.replace("yumm1234", "a1234567"))).isEmpty();
    }

    @Test
    @DisplayName("처리하지 못한 예외의 500 응답에는 예외 메시지가 실리지 않는다")
    void runtimeExceptionDoesNotLeakMessage() {
        String leak = "ERROR: null value in column \"nickname\" of relation \"users\" violates not-null constraint";

        ResponseEntity<Map<String, Object>> response =
                new GlobalExceptionHandler().handleRuntimeException(new IllegalStateException(leak));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().toString()).doesNotContain(leak).doesNotContain("users", "nickname");
        assertThat(response.getBody().get("message")).isEqualTo(ErrorCode.INTERNAL_ERROR.getMessage());
    }
}
