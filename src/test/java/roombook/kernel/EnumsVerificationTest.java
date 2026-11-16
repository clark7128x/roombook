package roombook.kernel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("A-30")
@Tag("A-33")
@Tag("A-56")
class EnumsVerificationTest {

    @Test
    @DisplayName("A-30, A-33: Ідентифікатори Reason дослівно збігаються з 13 кодами ядра з таблиці етапу 5")
    void reasonIdentifiersMatchStage5Table() {
        List<String> expectedReasons = List.of(
                "INVALID_INPUT",
                "INVALID_TIME",
                "ROOM_NOT_FOUND",
                "ROOM_INACTIVE",
                "SLOT_TAKEN",
                "BOOKING_NOT_FOUND",
                "NOT_PERMITTED",
                "BOOKING_FINISHED",
                "ROOM_NAME_TAKEN",
                "ROOM_HAS_UNFINISHED_BOOKINGS",
                "USERNAME_TAKEN",
                "USER_NOT_FOUND",
                "LAST_ADMIN", "INVITATION_INVALID"
        );

        List<String> actualReasons = Arrays.stream(Reason.values())
                .map(Enum::name)
                .toList();

        assertEquals(expectedReasons, actualReasons, "Перелік ідентифікаторів Reason має дослівно збігатися з таблицею етапу 5");
        assertEquals(14, Reason.values().length);
    }

    @Test
    @DisplayName("A-33: Ідентифікатори TimeRule дослівно збігаються з 6 підкодами INVALID_TIME з таблиці етапу 5")
    void timeRuleIdentifiersMatchStage5Table() {
        List<String> expectedRules = List.of(
                "INTERVAL_SHAPE",
                "OUTSIDE_WORK_HOURS",
                "NOT_ON_GRID",
                "BAD_DURATION",
                "IN_THE_PAST",
                "TOO_FAR"
        );

        List<String> actualRules = Arrays.stream(TimeRule.values())
                .map(Enum::name)
                .toList();

        assertEquals(expectedRules, actualRules, "Перелік підкодів TimeRule має дослівно збігатися з таблицею етапу 5");
        assertEquals(6, TimeRule.values().length);
    }

    @Test
    @DisplayName("A-56: Ідентифікатори FieldRule містять базові правила валідації полів")
    @Tag("A-56")
    void fieldRuleIdentifiersMatchSpecification() {
        List<String> expectedFieldRules = List.of(
                "REQUIRED",
                "LENGTH",
                "RANGE",
                "FORMAT",
                "ILLEGAL_CHARACTERS",
                "ILLEGAL_STATE"
        );

        List<String> actualFieldRules = Arrays.stream(FieldRule.values())
                .map(Enum::name)
                .toList();

        assertEquals(expectedFieldRules, actualFieldRules);
    }

    @Test
    @DisplayName("Role містить EMPLOYEE та ADMIN")
    void roleIdentifiersMatchSpecification() {
        List<String> expectedRoles = List.of("EMPLOYEE", "ADMIN");
        List<String> actualRoles = Arrays.stream(Role.values())
                .map(Enum::name)
                .toList();

        assertEquals(expectedRoles, actualRoles);
    }
}
