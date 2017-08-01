package com.keendly.api;

import com.keendly.adaptor.model.FeedEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class FeedUtils {

    public static Map<String, List<FeedEntry>> getNewest(Map<String, List<FeedEntry>> feeds, int number){
        Map<Date, String> sorted = new TreeMap<>((Comparator<Date>) (o1, o2) -> o2.compareTo(o1));

        feeds.entrySet().stream().forEach(e -> {
            e.getValue().stream().forEach(entry -> {
                sorted.put(entry.getPublished(), e.getKey() + ":" + entry.getId());
            });
        });

        Map<String, List<FeedEntry>> ret = new HashMap<>();

        List<String> sortedIds = sorted.values().stream().limit(number).collect(Collectors.toList());

        for (Map.Entry<String, List<FeedEntry>> entry : feeds.entrySet()){
            for (FeedEntry feedEntry : entry.getValue()){
                if (sortedIds.contains(entry.getKey() + ":" + feedEntry.getId())){
                    if (ret.containsKey(entry.getKey())){
                        ret.get(entry.getKey()).add(feedEntry);
                    } else {
                        List<FeedEntry> entries = new ArrayList<>();
                        entries.add(feedEntry);
                        ret.put(entry.getKey(), entries);
                    }
                }
            }

        }

        return ret;
    }
}
