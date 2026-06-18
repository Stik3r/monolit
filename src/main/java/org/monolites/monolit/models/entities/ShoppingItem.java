package org.monolites.monolit.models.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.monolites.monolit.models.enums.ShoppingItemStatus;

import java.time.Instant;

@Entity
@Table(name = "shopping_item", indexes = {
        @Index(name = "shopping_item_status_created_at_idx", columnList = "status, created_at")
})
@Getter
@Setter
public class ShoppingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ShoppingItemStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
