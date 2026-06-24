package org.monolites.monolit.models.dtos;

import java.util.List;

public record NewsDetails(String text, List<String> imageUrls) {
}
