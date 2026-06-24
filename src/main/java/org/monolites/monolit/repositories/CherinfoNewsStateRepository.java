package org.monolites.monolit.repositories;

import org.monolites.monolit.models.entities.CherinfoNewsState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CherinfoNewsStateRepository extends JpaRepository<CherinfoNewsState, String> {
}
