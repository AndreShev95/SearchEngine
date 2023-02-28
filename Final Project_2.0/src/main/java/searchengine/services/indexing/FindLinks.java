package searchengine.services.indexing;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.select.Elements;
import org.jsoup.nodes.Document;
import searchengine.config.ConfigConnect;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.repositories.LemmaRepository;
import searchengine.model.repositories.PageRepository;
import searchengine.model.repositories.SearchIndexRepository;
import searchengine.model.repositories.SiteRepository;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class FindLinks extends RecursiveAction {
    private final String link;
    private final String ROOT_LINK;
    private final ConcurrentHashMap<String, Page> resultForkJoinPageIndexer;
    private final AtomicBoolean indexingInProcess;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final SearchIndexRepository searchIndexRepository;
    private final ConfigConnect configConnect;
    private Set<String> absUrls = new HashSet<>();

    public FindLinks(String link, String ROOT_LINK, ConcurrentHashMap<String, Page> resultForkJoinPageIndexer,
                     AtomicBoolean indexingInProcess, SiteRepository siteRepository,
                     PageRepository pageRepository, LemmaRepository lemmaRepository,
                     SearchIndexRepository searchIndexRepository, ConfigConnect configConnect) {
        this.link = link;
        this.ROOT_LINK = ROOT_LINK;
        this.resultForkJoinPageIndexer = resultForkJoinPageIndexer;
        this.indexingInProcess = indexingInProcess;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.searchIndexRepository = searchIndexRepository;
        this.configConnect = configConnect;
    }

    @Override
    protected void compute() {
        if (resultForkJoinPageIndexer.containsKey(link) || !indexingInProcess.get()) {
            return;
        }
        Page page = new Page();
        page.setPath(link.substring(ROOT_LINK.length() - 1));
        page.setSiteId(getSiteId(ROOT_LINK));
        try {
            Thread.sleep(300);
            Connection connection = Jsoup.connect(link)
                    .userAgent(configConnect.getUserAgent())
                    .referrer(configConnect.getReferrer());
            Document doc = connection.timeout(60000).get();
            absUrls = getAbsUrls(doc);
            page.setContent(doc.outerHtml());
            page.setCode(doc.connection().response().statusCode());
        } catch (IOException ex) {
            page.setContent("");
            page.setCode(500);
            System.out.println("Не удается получить доступ к сайту - " + link + " - " + ex.getMessage());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (resultForkJoinPageIndexer.containsKey(link) || !indexingInProcess.get()) {
            return;
        }
        resultForkJoinPageIndexer.putIfAbsent(link, page);
        pageRepository.save(page);
        makeAnalyzeText(page);
        updateTimeStatusSite();
        CopyOnWriteArrayList<FindLinks> linkList = new CopyOnWriteArrayList<>();
        for (String ref : absUrls) {
            if (!resultForkJoinPageIndexer.containsKey(ref) && indexingInProcess.get()) {
                FindLinks task = new FindLinks(ref, ROOT_LINK, resultForkJoinPageIndexer, indexingInProcess,
                        siteRepository, pageRepository, lemmaRepository,
                        searchIndexRepository, configConnect);
                task.fork();
                linkList.add(task);
            }
        }
        for (FindLinks task : linkList) {
            if (!indexingInProcess.get()) {
                return;
            }
            task.join();
        }
    }

    private Set<String> getAbsUrls(Document document) {
        Elements elements = document.select("a[href]");
        return elements.stream().map(el -> el.attr("abs:href"))
                .filter(y -> y.startsWith(ROOT_LINK))
                .filter(v -> !v.contains("#") && !v.contains("?") && !v.contains("'") &&
                        !v.contains("&") && !v.contains("="))
                .filter(w -> !w.matches("([^\\s]+(\\.(?i)(jpg|png|gif|bmp|pdf))$)"))
                .filter(x -> !resultForkJoinPageIndexer.containsKey(x))
                .collect(Collectors.toSet());
    }

    private void makeAnalyzeText(Page page) {
        if (page.getCode() == 200) {
            TextAnalyzer textAnalyzer = new TextAnalyzer();
            textAnalyzer.analyze(page, lemmaRepository, searchIndexRepository);
        }
    }

    private void updateTimeStatusSite() {
        Site site = siteRepository.findSitesByUrl(ROOT_LINK).get(0);
        site.setStatusTime(new Timestamp(System.currentTimeMillis()));
        siteRepository.save(site);
    }

    private Integer getSiteId(String path) {
        return siteRepository.findSitesByUrl(path).get(0).getId();
    }
}