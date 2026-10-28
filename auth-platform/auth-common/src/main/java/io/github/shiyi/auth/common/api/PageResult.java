package io.github.shiyi.auth.common.api;

import java.util.List;

/**
 * Standard pagination envelope.
 */
public record PageResult<T>(List<T> records, long total, int page, int size) {
}
