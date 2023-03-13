package searchengine.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.indexing.IndexingService;
import searchengine.services.search.SearchService;
import searchengine.services.statistics.StatisticsService;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;
    private final SearchService searchService;
    private final AtomicBoolean indexingInProcess = new AtomicBoolean();
    private final SitesList sites;

    @Autowired
    public ApiController(StatisticsService statisticsService, IndexingService indexingService,
                         SearchService searchService, SitesList sites) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
        this.searchService = searchService;
        indexingInProcess.set(false);
        this.sites = sites;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<IndexingResponse> startIndexing() {
        if (indexingInProcess.get()) {
            IndexingResponse responseStartFail = new IndexingResponse();
            responseStartFail.setResult(false);
            responseStartFail.setError("Индексация уже запущена");
            return ResponseEntity.ok(responseStartFail);
        } else {
            indexingInProcess.set(true);
            IndexingResponse responseStartSuccess = new IndexingResponse();
            responseStartSuccess.setResult(true);
            new Thread(() -> indexingService.getIndexing(indexingInProcess)).start();
            return ResponseEntity.ok(responseStartSuccess);
        }
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<IndexingResponse> stopIndexing() {
        if (!indexingInProcess.get()) {
            IndexingResponse responseStopFail = new IndexingResponse();
            responseStopFail.setResult(false);
            responseStopFail.setError("Индексация не запущена");
            return ResponseEntity.ok(responseStopFail);
        } else {
            indexingInProcess.set(false);
            IndexingResponse responseStopSuccess = new IndexingResponse();
            responseStopSuccess.setResult(true);
            new Thread(() -> indexingService.stopIndexing(indexingInProcess)).start();
            return ResponseEntity.ok(responseStopSuccess);
        }
    }

    @PostMapping("/indexPage")
    public ResponseEntity<IndexingResponse> indexPage(String url) {
        boolean correctUrl = false;
        List<Site> sitesList = sites.getSites();
        String rootUrl = "";
        String rootName = "";
        for (Site site : sitesList) {
            if (url.contains(site.getUrl())) {
                correctUrl = true;
                rootUrl = site.getUrl();
                rootName = site.getName();
                break;
            }
        }
        if (correctUrl) {
            indexingInProcess.set(true);
            IndexingResponse responseIndexPageSuccess = new IndexingResponse();
            responseIndexPageSuccess.setResult(true);
            String finalRootUrl = rootUrl;
            String finalRootName = rootName;
            new Thread(() -> indexingService.indexPage(url, finalRootUrl, finalRootName, indexingInProcess)).start();
            return ResponseEntity.ok(responseIndexPageSuccess);
        } else {
            IndexingResponse responseIndexPageFail = new IndexingResponse();
            responseIndexPageFail.setResult(false);
            responseIndexPageFail.setError("Данная страница находится за пределами сайтов, " +
                    "указанных в конфигурационном файле");
            return ResponseEntity.ok(responseIndexPageFail);
        }
    }

    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(String query, String site, Integer offset, Integer limit) {
        if (indexingInProcess.get()) {
            SearchResponse responseFail = new SearchResponse();
            responseFail.setResult(false);
            responseFail.setError("Идет индексация, сначала нужно ее завершить");
            return ResponseEntity.ok(responseFail);
        } else {
            return ResponseEntity.ok(searchService.search(query, site, offset, limit));
        }
    }
}
