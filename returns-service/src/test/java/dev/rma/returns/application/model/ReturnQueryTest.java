package dev.rma.returns.application.model;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReturnQueryTest {
    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,-1", "0,101"})
    void rejectsInvalidPagination(int page, int size) {
        assertThatIllegalArgumentException().isThrownBy(() -> new ReturnQuery(page, size, null));
    }

    @ParameterizedTest
    @CsvSource({"0,1,0", "0,100,0", "2,20,40", "2147483647,100,214748364700"})
    void calculatesOffsetWithoutIntegerOverflow(int page, int size, long offset) {
        assertThat(new ReturnQuery(page, size, null).offset()).isEqualTo(offset);
    }

    @Test
    void permitsOmittingStatusFilter() {
        assertThat(new ReturnQuery(0, ReturnQuery.DEFAULT_SIZE, null).status()).isNull();
    }
}
