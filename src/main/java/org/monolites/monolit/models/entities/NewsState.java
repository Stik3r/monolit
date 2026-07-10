package org.monolites.monolit.models.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Date;

@Entity
@Table(name = "news_state")
@Getter
@Setter
public class NewsState {

    @Id
    @Column(name = "state_key", nullable = false, length = 64)
    private String stateKey;

    @Column(name = "latest_news_url", length = 1024)
    private String latestNewsUrl;

    @Column(name = "latest_news_date")
    private Date latestNewsDate;
}
