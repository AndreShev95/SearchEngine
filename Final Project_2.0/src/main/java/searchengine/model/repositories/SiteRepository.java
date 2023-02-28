package searchengine.model.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.Site;

import java.util.List;

@Repository
public interface SiteRepository extends JpaRepository<Site, Integer> {

    @Query("SELECT s FROM Site s WHERE s.url = ?1")
    List<Site> findSitesByUrl(String url);

}
