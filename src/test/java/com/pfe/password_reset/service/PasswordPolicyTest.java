package com.pfe.password_reset.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void accepts_validPassword() {
        assertThatCode(() -> policy.validate("Str0ngPass!")).doesNotThrowAnyException();
    }

    @Test
    void rejects_null() {
        assertThatThrownBy(() -> policy.validate(null))
                .isInstanceOf(PasswordPolicy.PasswordPolicyException.class);
    }

    @Test
    void rejects_tooShort() {
        assertThatThrownBy(() -> policy.validate("a1b2"))
                .isInstanceOf(PasswordPolicy.PasswordPolicyException.class);
    }

    @Test
    void rejects_tooLong() {
        assertThatThrownBy(() -> policy.validate("x".repeat(PasswordPolicy.MAX_LENGTH + 1)))
                .isInstanceOf(PasswordPolicy.PasswordPolicyException.class);
    }

    @Test
    void rejects_onlySpaces() {
        assertThatThrownBy(() -> policy.validate("          "))
                .isInstanceOf(PasswordPolicy.PasswordPolicyException.class);
    }

    @Test
    void doesNotTrimInternalSpaces_validWhenLongEnough() {
        // Internal spaces are legitimate password characters and must be kept.
        assertThatCode(() -> policy.validate("a b c d e")).doesNotThrowAnyException();
    }
}
