package org.monolites.monolit.repositories;

import org.monolites.monolit.models.entities.ShoppingItem;
import org.monolites.monolit.models.enums.ShoppingItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShoppingItemRepository extends JpaRepository<ShoppingItem, Long> {

    List<ShoppingItem> findAllByOrderByCreatedAtAscIdAsc();

    long countByStatus(ShoppingItemStatus status);

    long deleteByStatus(ShoppingItemStatus status);
}
