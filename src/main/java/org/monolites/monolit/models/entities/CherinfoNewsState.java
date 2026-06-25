package org.monolites.monolit.models.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "cherinfo_news_state")
@Getter
@Setter
public class CherinfoNewsState {

    @Id
    @Column(name = "state_key", nullable = false, length = 64)
    private String stateKey;

    @Column(name = "latest_news_url", length = 1024)
    private String latestNewsUrl;

    @Lob
    @Column(name = "sent_urls", nullable = false)
    private String sentUrls;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
