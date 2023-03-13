package searchengine.services.indexing;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.SearchIndex;
import searchengine.model.repositories.LemmaRepository;
import searchengine.model.repositories.SearchIndexRepository;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class TextAnalyzer {
    private final HashMap<String, HashSet<String>> formsLemmas = new HashMap<>();

    public void analyze(Page page, LemmaRepository lemmaRepository,
                        SearchIndexRepository searchIndexRepository, AtomicBoolean indexingInProcess) {
        String text = getText(page);
        if (!text.equals("")) {
            String[] words = getArrayWords(text);
            List<String> listWords = selectWords(words, indexingInProcess);
            if (!indexingInProcess.get()) {
                return;
            }
            HashMap<String, Integer> repeatsWords = calculateRepeats(listWords);
            List<String> lemmasList = lemmaRepository.findAllLemmas();
            Integer pageId = page.getId();
            repeatsWords.forEach((key, value) -> {
                HashSet<String> listLemmas = formsLemmas.get(key);
                Lemma lemma = new Lemma();
                if (lemmasList.contains(key)) {
                    lemma = lemmaRepository.findLemmasByLemma(key).get(0);
                    lemma.setFrequency(lemma.getFrequency() + 1);
                    String[] arrayLemmas = lemma.getFormsLemmas().split(",");
                    Collections.addAll(listLemmas, arrayLemmas);
                } else {
                    lemma.setSiteId(page.getSiteId());
                    lemma.setLemma(key);
                    lemma.setFrequency(1);
                }
                lemma.setFormsLemmas(String.join(",", listLemmas));
                lemmaRepository.save(lemma);
                Integer idLemma = getLemmaId(key, lemmaRepository);
                SearchIndex searchIndex = new SearchIndex();
                searchIndex.setPageId(pageId);
                searchIndex.setLemmaId(idLemma);
                searchIndex.setRank(value.floatValue());
                searchIndexRepository.save(searchIndex);
            });
        }
    }

    private String getText(Page page) {
        String content = page.getContent();
        Document document = Jsoup.parse(content);
        StringBuilder titleBuilder = new StringBuilder();
        StringBuilder bodyBuilder = new StringBuilder();

        if (content.contains("</title>")) {
            Elements elementsTitle = document.getElementsByTag("title");
            elementsTitle.forEach(element -> titleBuilder.append(element.text()));
        }
        if (content.contains("</body>")) {
            Elements elementsBody = document.getElementsByTag("body");
            elementsBody.forEach(element -> bodyBuilder.append(element.text()));
        }
        titleBuilder.append(bodyBuilder);
        return titleBuilder.toString();
    }

    private String[] getArrayWords(String text) {
        return text.replaceAll("[^a-zA-Zа-яА-ЯёЁ]", " ").replaceAll("ё", "е").
                replaceAll("Ё", "Е").trim().split("\\s+");
    }

    private List<String> selectWords(String[] words, AtomicBoolean indexingInProcess) {
        ArrayList<String> listWords = new ArrayList<>();
        for (String w : words) {
            if (!indexingInProcess.get()) {
                break;
            }
            String s = w.toLowerCase();
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
                listWords.add(word);
                HashSet<String> list = new HashSet<>();
                if (formsLemmas.containsKey(word)) {
                    list = formsLemmas.get(word);
                }
                list.add(word);
                list.add(w);
                formsLemmas.put(word, list);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return new ArrayList<>(listWords);
    }

    private HashMap<String, Integer> calculateRepeats(List<String> list) {
        HashMap<String, Integer> repeats = new HashMap<>();
        for (String word : list) {
            int oldCount = 0;
            if (repeats.get(word) != null) {
                oldCount = repeats.get(word);
            }
            repeats.put(word, oldCount + 1);
        }
        return repeats;
    }

    private Integer getLemmaId(String lemma, LemmaRepository lemmaRepository) {
        return lemmaRepository.findLemmasByLemma(lemma).get(0).getId();
    }
}