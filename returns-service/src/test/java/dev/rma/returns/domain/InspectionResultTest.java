package dev.rma.returns.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.rma.returns.domain.enums.ItemGrade;

public class InspectionResultTest {

    private static final String ITEM_ID = "item-1";
    private static final ItemGrade GRADE = ItemGrade.A;
    private static final String NOTE = "Item looks good";
    private static final List<String> PHOTO_KEYS = List.of("photo-1.jpg");

    @Test
    void shouldRejectNullItemId() {
        assertThatThrownBy(() -> new InspectionResult(null, GRADE, NOTE, PHOTO_KEYS))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("itemId must not be null");
    }

    @Test
    void shouldRejectNullGrade() {
        assertThatThrownBy(() -> new InspectionResult(ITEM_ID, null, NOTE, PHOTO_KEYS))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("grade must not be null");
    }

    @Test
    void shouldAllowNullNote() {
        InspectionResult result = new InspectionResult(ITEM_ID, GRADE, null, PHOTO_KEYS);

        assertThat(result.note()).isNull();
    }

    @Test
    void shouldAllowEmptyPhotoKeys() {
        InspectionResult result = new InspectionResult(ITEM_ID, GRADE, NOTE, List.of());

        assertThat(result.photoKeys()).isEmpty();
    }

    @Test
    void shouldRejectNoteLongerThan500Characters() {
        String tooLongNote = "a".repeat(501);

        assertThatThrownBy(() -> new InspectionResult(ITEM_ID, GRADE, tooLongNote, PHOTO_KEYS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("note must not exceed 500 characters");
    }

    @Test
    void shouldDefensivelyCopyPhotoKeys() {
        List<String> photoKeys = new ArrayList<>();
        photoKeys.add("photo-1.jpg");

        InspectionResult result = new InspectionResult(ITEM_ID, GRADE, NOTE, photoKeys);

        photoKeys.add("photo-2.jpg");

        assertThat(result.photoKeys()).containsExactly("photo-1.jpg");
    }

    @Test
    void shouldExposeImmutablePhotoKeys() {
        InspectionResult result = new InspectionResult(ITEM_ID, GRADE, NOTE, PHOTO_KEYS);

        assertThatThrownBy(() -> result.photoKeys().add("photo-2.jpg"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
