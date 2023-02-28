package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import searchengine.config.ConfigConnect;
import searchengine.config.SitesList;
import searchengine.model.*;
import searchengine.model.repositories.LemmaRepository;
import searchengine.model.repositories.PageRepository;
import searchengine.model.repositories.SearchIndexRepository;
import searchengine.model.repositories.SiteRepository;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    private final SitesList sites;
    private final ConfigConnect configConnect;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final SearchIndexRepository searchIndexRepository;
    private AtomicBoolean indexingInProcess;


    @Override
    public void getIndexing(AtomicBoolean indexingInProcess) {
        this.indexingInProcess = indexingInProcess;
        try {
            List<Thread> indexingThreadList = new ArrayList<>();
            List<searchengine.config.Site> sitesList = sites.getSites();
            for (searchengine.config.Site siteConfig : sitesList) {
                String pathSite = siteConfig.getUrl();
                String nameSite = siteConfig.getName();

                if (checkToExistenceSite(pathSite)) {
                    deleteDataFromDB(pathSite);
                }
                Thread thread = new Indexer(pathSite, nameSite, siteRepository,
                        pageRepository, lemmaRepository, searchIndexRepository,
                        configConnect, indexingInProcess);
                indexingThreadList.add(thread);
                thread.start();
            }
            for (Thread thread : indexingThreadList) {
                thread.join();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        indexingInProcess.set(false);
    }

    @Override
    public void stopIndexing(AtomicBoolean indexingInProcess) {
        this.indexingInProcess = indexingInProcess;
    }

    @Override
    public void indexPage(String url, String rootUrl, String rootName) {
        setIndexingStatusSite(rootUrl, rootName);
        int count = pageRepository.findPagesByUrl(url.substring(rootUrl.length() - 1)).size();
        if (count != 0) {
            Integer idPage = getPageId(url.substring(rootUrl.length() - 1));
            Page pageForDelete = pageRepository.findById(idPage).orElse(new Page());
            pageRepository.delete(pageForDelete);
        }
        Integer idSite = getSiteId(rootUrl);
        Page page = new Page();
        page.setSiteId(idSite);
        page.setPath(url.substring(rootUrl.length() - 1));
        try {
            Connection connection = Jsoup.connect(url)
                    .userAgent(configConnect.getUserAgent())
                    .referrer(configConnect.getReferrer());
            Document doc = connection.timeout(60000).get();
            page.setContent(doc.outerHtml());
            page.setCode(doc.connection().response().statusCode());
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        pageRepository.save(page);
        if (page.getCode() == 200) {
            makeAnalyzeText(page);
            setIndexedStatusSite(idSite);
        }
    }

    private void makeAnalyzeText(Page page) {
        TextAnalyzer textAnalyzer = new TextAnalyzer();
        textAnalyzer.analyze(page, lemmaRepository, searchIndexRepository);
    }

    private Boolean checkToExistenceSite(String path) {
        List<Site> sites = siteRepository.findAll();
        for (Site site : sites) {
            if (site.getUrl().equals(path)) {
                return true;
            }
        }
        return false;
    }

    private void deleteDataFromDB(String pathSite) {
        Integer idSite = getSiteId(pathSite);
        siteRepository.deleteById(idSite);
    }

    private void setIndexingStatusSite(String url, String name) {
        Boolean existsSite = checkToExistenceSite(url);
        Site site = new Site();
        if (!existsSite) {
            site.setName(name);
            site.setUrl(url);
        } else {
            site = siteRepository.findSitesByUrl(url).get(0);
        }
        site.setStatus(Status.INDEXING);
        site.setLastError(null);
        site.setStatusTime(new Timestamp(System.currentTimeMillis()));
        siteRepository.save(site);
    }

    private void setIndexedStatusSite(Integer idSite) {
        Site site = siteRepository.findById(idSite).orElse(new Site());
        site.setStatus(Status.INDEXED);
        site.setLastError(null);
        site.setStatusTime(new Timestamp(System.currentTimeMillis()));
        siteRepository.save(site);
    }

    private Integer getSiteId(String path) {
        return siteRepository.findSitesByUrl(path).get(0).getId();
    }

    private Integer getPageId(String path) {
        return pageRepository.findPagesByUrl(path).get(0).getId();
    }
}