package searchengine.services.statistics;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Site;
import searchengine.model.repositories.LemmaRepository;
import searchengine.model.repositories.PageRepository;
import searchengine.model.repositories.SiteRepository;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;

    @Override
    public StatisticsResponse getStatistics() {
        TotalStatistics total = new TotalStatistics();
        total.setSites(siteRepository.findAll().size());
        total.setIndexing(true);

        List<DetailedStatisticsItem> detailed = new ArrayList<>();

        if (total.getSites() > 0) {
            List<Site> sitesList = siteRepository.findAll();
            for (Site site : sitesList) {
                DetailedStatisticsItem item = getItem(site);
                total.setPages(total.getPages() + item.getPages());
                total.setLemmas(total.getLemmas() + item.getLemmas());
                detailed.add(item);
            }
        }
        else {
            total.setPages(0);
            total.setLemmas(0);
        }

        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);

        StatisticsResponse response = new StatisticsResponse();
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }

    private DetailedStatisticsItem getItem(Site site){
        DetailedStatisticsItem item = new DetailedStatisticsItem();
        item.setName(site.getName());
        item.setUrl(site.getUrl());
        item.setStatus(site.getStatus().name());
        if (site.getLastError() != null) {
            item.setError(site.getLastError());
        }
        Integer idSite = site.getId();
        item.setPages(pageRepository.countBySiteId(idSite));
        item.setLemmas(lemmaRepository.countBySiteId(idSite));
        item.setStatusTime(site.getStatusTime().getTime());
        return item;
    }
}
