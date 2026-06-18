package org.monolites.monolit.models.dtos;

import org.monolites.monolit.models.enums.ShoppingListAction;

public record ShoppingListActionDto(ShoppingListAction action, Long itemId, int page) {
}
