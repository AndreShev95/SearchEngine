package searchengine.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "lemma")
public class Lemma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "site_id", nullable = false)
    private Integer siteId;

    @Column(columnDefinition = "VARCHAR(255)", nullable = false)
    private String lemma;

    @Column(nullable = false)
    private Integer frequency;

    @ManyToOne
    @JoinColumn(name = "site_id", insertable = false, updatable = false, nullable = false)
    private Site site;

    @OneToMany(mappedBy = "lemma", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SearchIndex> searchIndices;

    @Column(name = "forms_lemmas", columnDefinition = "MEDIUMTEXT", nullable = false)
    private String formsLemmas;

    @Override
    public String toString() {
        return "model.Lemma{" +
                "id=" + id +
                ", lemma='" + lemma + '\'' +
                ", frequency=" + frequency +
                ", formsLemmas='" + formsLemmas + '\'' +
                ", siteId=" + siteId +
                '}';
    }
}
