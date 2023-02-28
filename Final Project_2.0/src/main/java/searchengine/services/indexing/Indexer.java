package searchengine.services.indexing;

import searchengine.config.ConfigConnect;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.model.repositories.LemmaRepository;
import searchengine.model.repositories.PageRepository;
import searchengine.model.repositories.SearchIndexRepository;
import searchengine.model.repositories.SiteRepository;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

public class Indexer extends Thread {

    private final String pathSite;
    private final String nameSite;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final SearchIndexRepository searchIndexRepository;
    private final ConfigConnect configConnect;
    private final AtomicBoolean indexingInProcess;

    private static final String printStopError = "Индексация остановлена пользователем";

    public Indexer(String pathSite, String nameSite, SiteRepository siteRepository,
                   PageRepository pageRepository, LemmaRepository lemmaRepository,
                   SearchIndexRepository searchIndexRepository, ConfigConnect configConnect,
                   AtomicBoolean indexingInProcess) {
        this.pathSite = pathSite;
        this.nameSite = nameSite;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.searchIndexRepository = searchIndexRepository;
        this.configConnect = configConnect;
        this.indexingInProcess = indexingInProcess;
    }

    @Override
    public void run() {
        try {
            fillBase();
            if (!indexingInProcess.get()) {
                failedUpdate(printStopError);
            } else {
                updateIndexedStatusSite();
            }
        } catch (IOException e) {
            e.printStackTrace();
            failedUpdate(e.getMessage());
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }

    public void fillBase() throws InterruptedException, IOException {
        Site site = new Site();
        site.setName(nameSite);
        site.setStatus(Status.INDEXING);
        site.setLastError(null);
        site.setStatusTime(new Timestamp(System.currentTimeMillis()));
        site.setUrl(pathSite);
        siteRepository.save(site);

        ConcurrentHashMap<String, Page> resultForkJoinPageIndexer = new ConcurrentHashMap<>();
        new ForkJoinPool().invoke(new FindLinks(pathSite, pathSite, resultForkJoinPageIndexer,
                indexingInProcess, siteRepository, pageRepository, lemmaRepository,
                searchIndexRepository, configConnect));
    }

    private void failedUpdate(String error) {
        Integer idSite = siteRepository.findSitesByUrl(pathSite).get(0).getId();
        Site site = new Site();
        if (idSite != null) {
            site = siteRepository.findSitesByUrl(pathSite).get(0);
        }
        site.setLastError(error);
        site.setName(nameSite);
        site.setStatus(Status.FAILED);
        site.setStatusTime(new Timestamp(System.currentTimeMillis()));
        site.setUrl(pathSite);
        siteRepository.save(site);
    }

    private void updateIndexedStatusSite() {
        Site site = siteRepository.findSitesByUrl(pathSite).get(0);
        site.setStatus(Status.INDEXED);
        site.setLastError(null);
        site.setStatusTime(new Timestamp(System.currentTimeMillis()));
        siteRepository.save(site);
    }
}
