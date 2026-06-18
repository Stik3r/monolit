package org.monolites.monolit.repositories;

import org.monolites.monolit.models.entities.ShoppingListDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShoppingListDraftRepository extends JpaRepository<ShoppingListDraft, Long> {
}
