package searchengine.services.indexing;

import java.util.concurrent.atomic.AtomicBoolean;

public interface IndexingService {
    void getIndexing(AtomicBoolean indexingInProcess);

    void stopIndexing(AtomicBoolean indexingInProcess);

    void indexPage(String url, String rootUrl, String rootName);
}
