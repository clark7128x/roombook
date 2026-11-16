package roombook.scheduling.application;

import java.util.List;

public record Page<T>(List<T> items, int page, int size, boolean hasNext) {
}
