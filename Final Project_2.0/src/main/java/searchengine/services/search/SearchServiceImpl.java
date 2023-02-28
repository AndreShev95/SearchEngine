package searchengine.services.search;

import lombok.RequiredArgsConstructor;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import searchengine.dto.search.SearchData;
import searchengine.dto.search.SearchResponse;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.SearchIndex;
import searchengine.model.Site;
import searchengine.model.repositories.LemmaRepository;
import searchengine.model.repositories.PageRepository;
import searchengine.model.repositories.SearchIndexRepository;
import searchengine.model.repositories.SiteRepository;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final SearchIndexRepository searchIndexRepository;
    private Float maxRelAbs = 0.0f;

    @Override
    public SearchResponse search(String query, String pathSite, Integer offset, Integer limit) {
        return getResult(query, pathSite, offset, limit);
    }

    private SearchResponse getResult(String query, String pathSite, Integer offset, Integer limit) {
        SearchResponse searchResponse = new SearchResponse();
        Integer searchSiteId = 0;
        if (pathSite != null) {
            searchSiteId = siteRepository.findSitesByUrl(pathSite).get(0).getId();
        }
        if (query.equals("")) {
            return getFailEmptyResponse(searchResponse);
        }
        String[] arrayString = getArrayWords(query);
        HashMap<String, Integer> mapWords = selectWords(arrayString);
        Map<List<Integer>, List<Integer>> listMap;
        if (!mapWords.isEmpty()) {
            listMap = findLemmaAndPageIds(mapWords, searchSiteId);
        } else {
            return getFailResponse(searchResponse);
        }
        List<SearchData> listResult = new ArrayList<>();
        for (Map.Entry<List<Integer>, List<Integer>> entry : listMap.entrySet()) {
            listResult = getResultList(entry.getKey(), entry.getValue());
        }
        List<SearchData> listAfterFilter = new ArrayList<>();
        int i = 1;
        for (SearchData searchData : listResult) {
            searchData.setRelevance(searchData.getRelevance() / maxRelAbs);
            if (i > offset & i <= limit) {
                listAfterFilter.add(searchData);
            }
            i++;
        }
        if (listAfterFilter.isEmpty()) {
            return getFailResponse(searchResponse);
        } else {
            searchResponse.setData(listAfterFilter);
            searchResponse.setResult(true);
            searchResponse.setCount(listResult.size());
            return searchResponse;
        }
    }

    private SearchResponse getFailEmptyResponse(SearchResponse failResponse) {
        failResponse.setResult(false);
        failResponse.setError("Задан пустой поисковый запрос");
        return failResponse;
    }

    private SearchResponse getFailResponse(SearchResponse failResponse) {
        failResponse.setResult(false);
        failResponse.setError("Данной фразы нет на сайте или задан слишком популярный запрос. Повторите запрос.");
        return failResponse;
    }

    private String[] getArrayWords(String text) {
        return text.replaceAll("[^a-zA-Zа-яА-ЯёЁ]", " ").toLowerCase().
                replaceAll("ё", "е").trim().split("\\s+");
    }

    private HashMap<String, Integer> selectWords(String[] words) {
        HashMap<String, Integer> mapLemmas = new HashMap<>();
        for (String s : words) {
            try {
                LuceneMorphology luceneMorph;
                if (s.matches("[а-я]+")) {
                    luceneMorph = new RussianLuceneMorphology();
                } else if (s.matches("[a-z]+")) {
                    luceneMorph = new EnglishLuceneMorphology();
                } else {
                    continue;
                }
                String word = luceneMorph.getMorphInfo(s.trim()).get(0);
                if (!(word.endsWith("МЕЖД") || word.endsWith("ПРЕДЛ")
                        || word.endsWith("ЧАСТ") || word.endsWith("СОЮЗ"))) {
                    word = word.substring(0, word.indexOf("|"));
                } else {
                    continue;
                }
                if (!mapLemmas.containsKey(word)) {
                    List<Lemma> lemmaList = lemmaRepository.findLemmasByLemma(word);
                    if (lemmaList.size() == 0) {
                        continue;
                    }
                    Integer frequency = lemmaList.get(0).getFrequency();
                    if (frequency < 0.95 * pageRepository.count()) {
                        mapLemmas.put(word, frequency);
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return mapLemmas.isEmpty() ? new HashMap<>() : mapLemmas.entrySet().stream().
                sorted(Map.Entry.comparingByValue()).collect
                        (Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    }

    private Map<List<Integer>, List<Integer>> findLemmaAndPageIds(HashMap<String, Integer> mapWords,
                                                                  Integer searchSiteId) {
        List<Integer> listPageId = new ArrayList<>();
        List<Integer> listLemmaId = new ArrayList<>();
        Map<List<Integer>, List<Integer>> listMap = new HashMap<>();
        for (Map.Entry<String, Integer> entry : mapWords.entrySet()) {
            Integer idLemma;
            List<Lemma> listLemma;
            if (searchSiteId > 0) {
                listLemma = lemmaRepository.
                        findLemmasByLemmaAndSiteId(entry.getKey(), searchSiteId);
            } else {
                listLemma = lemmaRepository.findLemmasByLemma(entry.getKey());
            }
            if (!listLemma.isEmpty()) {
                idLemma = listLemma.get(0).getId();
                List<SearchIndex> indexList = searchIndexRepository.findIndexesByLemmaId(idLemma);
                List<Integer> list = new ArrayList<>();
                for (SearchIndex index : indexList) {
                    if (listPageId.isEmpty() || listPageId.contains(index.getPageId())) {
                        list.add(index.getPageId());
                    }
                }
                if (list.isEmpty()) {
                    continue;
                }
                listPageId.clear();
                listPageId = list;
                listLemmaId.add(idLemma);
            }
        }
        listMap.put(listPageId, listLemmaId);
        return listMap;

    }

    private List<SearchData> getResultList(List<Integer> listPageId, List<Integer> listLemmaId) {
        List<SearchData> listResult = new ArrayList<>();
        for (Integer pageId : listPageId) {
            SearchData searchData = new SearchData();
            Page page = pageRepository.findById(pageId).orElse(new Page());
            Site site = siteRepository.findById(page.getSiteId()).orElse(new Site());
            searchData.setUri(page.getPath());
            searchData.setSite(site.getUrl());
            searchData.setSiteName(site.getName());
            String content = page.getContent();
            Document document = Jsoup.parse(content);
            String title = getTitle(content, document);
            searchData.setTitle(title);
            String text = getText(title, content, document);
            StringBuilder snippetBuilder = new StringBuilder();
            Float relevanceAbs = 0.0f;
            for (Integer lemmaId : listLemmaId) {
                relevanceAbs += searchIndexRepository.getRankIndexByPageAndLemmaIds(pageId, lemmaId);
                String listForms = lemmaRepository.findFormsLemmasById(lemmaId).get(0);
                String snippet = findSnippet(text, listForms);
                snippetBuilder.append(snippet);
            }
            if (relevanceAbs > maxRelAbs) {
                maxRelAbs = relevanceAbs;
            }
            searchData.setSnippet(snippetBuilder.toString());
            searchData.setRelevance(relevanceAbs);
            listResult.add(searchData);
        }
        return listResult;
    }

    private String getTitle(String content, Document document) {
        StringBuilder titleBuilder = new StringBuilder();
        if (content.contains("</title>")) {
            Elements elementsTitle = document.getElementsByTag("title");
            elementsTitle.forEach(element -> titleBuilder.append(element.text()));
        }
        return titleBuilder.toString();
    }

    private String getText(String title, String content, Document document) {
        StringBuilder textBuilder = new StringBuilder();
        textBuilder.append(title);
        if (content.contains("</body>")) {
            Elements elementsBody = document.getElementsByTag("body");
            elementsBody.forEach(element -> textBuilder.append(element.text()));
        }
        return textBuilder.toString();
    }

    private String findSnippet(String text, String stringWords) {
        HashSet<String> listWords = new HashSet<>(Arrays.asList(stringWords.split(",")));
        int around = 5;
        StringBuilder stringBuilder = new StringBuilder();
        for (String word3 : listWords) {
            String pattern = "([^ ]+ ?){0," + around + "}" + word3 + "[^a-zA-Zа-яА-ЯёЁ]"
                    + "( ?[^ ]+){0," + around + "}";
            Matcher m = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text);
            while (m.find()) {
                stringBuilder.append(m.group()
                        .replaceAll(word3, "<b>" + word3 + "</b>")).append("\n");
            }
        }
        return stringBuilder.toString();
    }
}