package searchengine.model.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import searchengine.model.Page;

import java.util.List;

public interface PageRepository extends JpaRepository<Page, Integer> {

    @Query("SELECT p FROM Page p WHERE p.path = ?1")
    List<Page> findPagesByUrl(String url);

    Integer countBySiteId(Integer siteId);
}
