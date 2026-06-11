package org.monolites.monolit.repositories;

import org.monolites.monolit.models.entities.ReminderCreationDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReminderCreationDraftRepository extends JpaRepository<ReminderCreationDraft, Long> {
}
