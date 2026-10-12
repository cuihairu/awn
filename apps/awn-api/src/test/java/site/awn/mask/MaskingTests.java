package site.awn.mask;

import org.junit.jupiter.api.Test;
import site.awn.shared.mask.Masking;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 01：脱敏规则单测。手机号/证件号/邮箱进入日志前必须遮蔽。
 */
class MaskingTests {

    @Test
    void phoneKeepsHeadAndTail() {
        assertThat(Masking.phone("13812345678")).isEqualTo("138****5678");
    }

    @Test
    void idCardKeepsHeadAndLastTwo() {
        assertThat(Masking.idCard("110101199001011234")).isEqualTo("1101**********34");
    }

    @Test
    void emailKeepsPrefixAndDomain() {
        assertThat(Masking.email("zhangsan@example.com")).isEqualTo("zh***@example.com");
        assertThat(Masking.email("a@b.cn")).isEqualTo("a***@b.cn");
    }

    @Test
    void referenceKeepsLastFourOnly() {
        assertThat(Masking.reference("ORD-1234567890")).isEqualTo("****7890");
        assertThat(Masking.reference("ORD1")).isEqualTo("****");
    }

    @Test
    void shortAndNullInputsDoNotThrow() {
        assertThat(Masking.phone(null)).isEmpty();
        // 短值整体遮蔽：不回显任何片段
        assertThat(Masking.phone("12345")).isEqualTo("****");
        assertThat(Masking.idCard("abc")).isEqualTo("****");
        assertThat(Masking.email(null)).isEmpty();
        assertThat(Masking.email("no-at-sign")).isEqualTo("no-at-sign");
        assertThat(Masking.reference(null)).isEmpty();
    }
}
