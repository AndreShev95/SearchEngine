package searchengine.model.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import searchengine.model.SearchIndex;

import java.util.List;

public interface SearchIndexRepository extends JpaRepository<SearchIndex, Integer> {

    @Query("SELECT i FROM SearchIndex i WHERE i.lemmaId = ?1")
    List<SearchIndex> findIndexesByLemmaId(Integer lemma_id);

    @Query("SELECT i.rank FROM SearchIndex i WHERE i.pageId = ?1 AND i.lemmaId = ?2")
    Float getRankIndexByPageAndLemmaIds(Integer pageId, Integer LemmaId);

}
