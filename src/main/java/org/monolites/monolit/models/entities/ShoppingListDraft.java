package org.monolites.monolit.models.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "shopping_list_draft")
@Getter
@Setter
public class ShoppingListDraft {

    public static final long SINGLE_USER_DRAFT_ID = 1L;

    @Id
    private Long id;

    @Lob
    @Column(name = "pending_items")
    private String pendingItems;
}
