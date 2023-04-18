package searchengine.model.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import searchengine.model.Lemma;

import java.util.List;

public interface LemmaRepository extends JpaRepository<Lemma, Integer> {

    @Query("SELECT l FROM Lemma l WHERE l.lemma = ?1")
    List<Lemma> findLemmasByLemma(String lemma);

    @Query("SELECT l FROM Lemma l WHERE l.siteId = ?1")
    List<Lemma> findLemmasBySiteId(Integer siteId);

    @Query("SELECT l FROM Lemma l WHERE l.lemma = ?1 AND l.siteId = ?2")
    List<Lemma> findLemmasByLemmaAndSiteId(String lemma, Integer siteId);

    @Query("SELECT l.formsLemmas FROM Lemma l WHERE l.id = ?1")
    List<String> findFormsLemmasById(Integer id);

    Integer countBySiteId(Integer siteId);
}
