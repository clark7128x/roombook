package roombook.scheduling.web.api;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import roombook.testsupport.HttpTestSupport;
import roombook.testsupport.TestDataBuilder;
import static org.junit.jupiter.api.Assertions.*;

class RoomApiTest extends HttpTestSupport {
    @Test @Tag("TS-08") @Tag("A-14") @Tag("A-36")
    void roomInputEnforcesNumericAndUnicodeBoundariesThroughJson() throws Exception {
        for (var input : java.util.List.of(
                java.util.Map.of("name", "😀".repeat(100), "floor", -5, "capacity", 1, "equipment", "ї".repeat(500)),
                java.util.Map.of("name", "Ї".repeat(100), "floor", 100, "capacity", 1000))) {
            var response = api("POST", "/api/v1/rooms", admin, mapper.writeValueAsString(input));
            assertEquals(201, response.statusCode(), response.body());
        }
        for (var input : java.util.List.of(
                java.util.Map.of("name", "😀".repeat(101), "floor", 1, "capacity", 8),
                java.util.Map.of("name", "Boundary", "floor", -6, "capacity", 8),
                java.util.Map.of("name", "Boundary", "floor", 101, "capacity", 8),
                java.util.Map.of("name", "Boundary", "floor", 1, "capacity", 0),
                java.util.Map.of("name", "Boundary", "floor", 1, "capacity", 1001),
                java.util.Map.of("name", "Boundary", "floor", 1, "capacity", 8, "equipment", "ї".repeat(501)))) {
            var response = api("POST", "/api/v1/rooms", admin, mapper.writeValueAsString(input));
            problem(response, 400, "INVALID_INPUT");
            assertFalse(json(response).get("errors").isEmpty());
        }
    }
    @Test @Tag("TS-08") @Tag("EP-01") @Tag("EP-02") @Tag("EP-03") @Tag("EP-04") @Tag("EP-05") @Tag("EP-06")
    void roomLifecycleIgnoresStateInjectionAndKeepsInactiveDetailReadable() throws Exception {
        var created = api("POST", "/api/v1/rooms", admin,
                "{\"name\":\"  Кімната  \n\",\"floor\":2,\"capacity\":8,\"active\":false}");
        // A control character is rejected even when it is inside a JSON string.
        problem(created, 400, "INVALID_INPUT");
        created = api("POST", "/api/v1/rooms", admin,
                "{\"name\":\"  Кімната  \",\"floor\":2,\"capacity\":8,\"active\":false}");
        assertEquals(201, created.statusCode(), created.body());
        long id = json(created).get("id").asLong();
        String path = "/api/v1/rooms/" + id;
        assertEquals(path, created.headers().firstValue("Location").orElseThrow());
        assertEquals("Кімната", json(created).get("name").asString());
        assertTrue(json(created).get("active").asBoolean());
        assertEquals(200, api("GET", path, employee, "").statusCode());
        assertFalse(json(api("POST", path + "/deactivate", admin, "")).get("active").asBoolean());
        assertEquals(200, api("GET", path, employee, "").statusCode());
        assertEquals(0, json(api("GET", "/api/v1/rooms", employee, "")).size());
        assertEquals(1, json(api("GET", "/api/v1/rooms?includeInactive=true", admin, "")).size());
        problem(api("GET", "/api/v1/rooms?includeInactive=true", employee, ""), 403, "NOT_PERMITTED");
        var updated = api("PUT", path, admin, "{\"name\":\"Нова\",\"floor\":1,\"capacity\":10,\"equipment\":\"  \",\"active\":true}");
        assertEquals(200, updated.statusCode(), updated.body());
        assertFalse(json(updated).get("active").asBoolean());
        assertTrue(json(updated).get("equipment").isNull());
        assertTrue(json(api("POST", path + "/activate", admin, "")).get("active").asBoolean());
        problem(api("PUT", path, admin, "{\"name\":\"Incomplete\"}"), 400, "INVALID_INPUT");
        problem(api("DELETE", path, admin, ""), 405, "UNSUPPORTED_REQUEST");
    }

    @Test @Tag("TS-08") @Tag("EP-07") @Tag("A-37") @Tag("TR-01")
    void occupancyDefaultsToOfficeTodayAndSortsRoomsInUkrainianOrder() throws Exception {
        TestDataBuilder.room().name("Єдина").floor(1).capacity(6).insert();
        TestDataBuilder.room().name("Екран").floor(1).capacity(8).insert();
        TestDataBuilder.room().name("Другий").floor(2).capacity(12).insert();
        var result = api("GET", "/api/v1/occupancy", employee, "");
        assertEquals(200, result.statusCode());
        assertEquals("2026-10-27", json(result).get("date").asString());
        var rooms = json(result).get("rooms");
        assertEquals(3, rooms.size());
        assertEquals("Екран", rooms.get(0).get("room").get("name").asString());
        assertEquals("Єдина", rooms.get(1).get("room").get("name").asString());
        assertEquals(0, rooms.get(0).get("bookings").size());
        assertEquals(1, json(api("GET", "/api/v1/occupancy?floor=1&minCapacity=7", employee, "")).get("rooms").size());
        for (String date : new String[]{"1999-12-31", "2101-01-01", "2026-02-30", "27.10.2026"}) {
            var response = api("GET", "/api/v1/occupancy?date=" + date, employee, "");
            problem(response, 400, "INVALID_INPUT");
            assertEquals("date", json(response).get("errors").get(0).get("field").asString());
        }
    }

    @Test @Tag("TS-08") @Tag("TS-09") @Tag("EP-01") @Tag("A-32")
    void freeIntervalRejectsOffsetsMissingBoundsAndInvalidTimeWithPreciseFields() throws Exception {
        long room = TestDataBuilder.room().name("Вільний інтервал").insert();
        TestDataBuilder.booking().roomId(room).authorId(employeeId)
                .startAt(java.time.LocalDateTime.parse("2026-10-27T10:00"))
                .endAt(java.time.LocalDateTime.parse("2026-10-27T11:00")).insert();
        assertEquals(0, json(api("GET", "/api/v1/rooms?freeFrom=2026-10-27T10:00&freeTo=2026-10-27T11:00", employee, "")).size());
        assertEquals(1, json(api("GET", "/api/v1/rooms?freeFrom=2026-10-27T11:00&freeTo=2026-10-27T12:00", employee, "")).size());
        for (String value : new String[]{"2026-10-27T10:00Z", "2026-10-27T10:00+03:00", "bad"}) {
            var response = api("GET", "/api/v1/rooms?freeFrom=" + query(value) + "&freeTo=2026-10-27T11:00", employee, "");
            problem(response, 400, "INVALID_INPUT");
            assertEquals("freeFrom", json(response).get("errors").get(0).get("field").asString());
        }
        problem(api("GET", "/api/v1/rooms?freeFrom=2026-10-27T10:00", employee, ""), 400, "INVALID_INPUT");
        problem(api("GET", "/api/v1/rooms?freeFrom=2026-10-27T10:00&freeTo=2026-10-27T11:00&includeInactive=true", admin, ""), 400, "INVALID_INPUT");
        var interval = api("GET", "/api/v1/rooms?freeFrom=2026-10-27T10:00&freeTo=2026-10-27T10:00", employee, "");
        problem(interval, 422, "INVALID_TIME");
        assertEquals("INTERVAL_SHAPE", json(interval).get("rule").asString());
        problem(api("GET", "/api/v1/rooms?minCapacity=0", employee, ""), 400, "INVALID_INPUT");
        problem(api("GET", "/api/v1/rooms?floor=no", employee, ""), 400, "INVALID_INPUT");
    }
}
