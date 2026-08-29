package com.example.demo;

import com.example.demo.dto.users.SignupRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 약관 동의(M4)는 컨트롤러의 @Valid가 걸러낸다.
 * Spring 컨텍스트 없이 Bean Validation만으로 검증한다.
 */
class SignupTermsAgreementTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static Set<ConstraintViolation<SignupRequest>> validate(String json) throws Exception {
        SignupRequest request = MAPPER.readValue(json, SignupRequest.class);
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            return validator.validate(request);
        }
    }

    private static String body(String agreedToTermsField) {
        return "{\"email\":\"a@b.com\",\"password\":\"pw123456\",\"nickname\":\"밥친구\","
                + "\"gender\":\"MALE\",\"birthYear\":1995" + agreedToTermsField + "}";
    }

    @Test
    @DisplayName("약관에 동의하면 가입 요청이 통과한다")
    void agreedPasses() throws Exception {
        assertThat(validate(body(",\"agreedToTerms\":true"))).isEmpty();
    }

    @Test
    @DisplayName("동의하지 않으면 거부된다")
    void notAgreedRejected() throws Exception {
        assertThat(validate(body(",\"agreedToTerms\":false")))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("agreedToTerms");
    }

    @Test
    @DisplayName("필드 자체가 빠져도 미동의로 보고 거부된다")
    void missingFieldRejected() throws Exception {
        assertThat(validate(body("")))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("agreedToTerms");
    }
}
