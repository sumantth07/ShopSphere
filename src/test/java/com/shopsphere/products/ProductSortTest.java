package com.shopsphere.products;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class ProductSortTest {
    @Test void defaultsToNewestDescendingWithIdTiebreaker() {
        Sort s = ProductSort.resolve(null, null);
        assertTrue(s.getOrderFor("createdAt").isDescending());
        assertNotNull(s.getOrderFor("id"));
    }
    @Test void rejectsUnknownSortField() { assertThrows(IllegalArgumentException.class, () -> ProductSort.resolve("password; drop table", "asc")); }
    @Test void rejectsBadDirection() { assertThrows(IllegalArgumentException.class, () -> ProductSort.resolve("price", "sideways")); }
    @Test void likeWildcardsAreEscaped() { assertEquals("50\\% off\\_x", ProductSpecs.escapeLike("50% off_x")); }
}
