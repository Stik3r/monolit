package org.monolites.monolit.repositories;

import org.monolites.monolit.models.entities.CustomReminder;
import org.monolites.monolit.models.enums.CustomReminderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface CustomReminderRepository extends JpaRepository<CustomReminder, Long> {

    List<CustomReminder> findAllByStatusAndScheduledAtGreaterThanEqualAndScheduledAtLessThanOrderByScheduledAtAsc(
            CustomReminderStatus status,
            Instant from,
            Instant to
    );

    List<CustomReminder> findAllByStatusAndScheduledAtBefore(CustomReminderStatus status, Instant before);

    List<CustomReminder> findAllByStatusInOrderByScheduledAtAsc(Collection<CustomReminderStatus> statuses);
}
