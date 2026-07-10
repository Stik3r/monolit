package org.monolites.monolit.repositories;

import org.monolites.monolit.models.entities.NewsState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NewsStateRepository extends JpaRepository<NewsState, String> {

    NewsState findNewsStateByStateKey(String stateKey);
}
