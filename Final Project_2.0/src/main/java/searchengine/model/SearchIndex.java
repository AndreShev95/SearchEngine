package searchengine.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "indexes")
public class SearchIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "page_id", nullable = false)
    private Integer pageId;

    @Column(name = "lemma_id", nullable = false)
    private Integer lemmaId;

    @Column(name = "rank_index", nullable = false)
    private Float rank;

    @ManyToOne
    @JoinColumn(name = "page_id", insertable = false, updatable = false, nullable = false)
    private Page page;

    @ManyToOne
    @JoinColumn(name = "lemma_id", insertable = false, updatable = false, nullable = false)
    private Lemma lemma;

    @Override
    public String toString() {
        return "SearchIndex{" +
                "id=" + id +
                ", pageId=" + pageId +
                ", lemmaId=" + lemmaId +
                ", rank=" + rank +
                '}';
    }
}
