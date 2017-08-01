package com.keendly.api;

import com.keendly.adaptor.model.FeedEntry;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.Assert;
import org.junit.Test;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestFeedUtils {

    @Test
    public void testGetNewest() throws ParseException {
        // given
        FeedEntry entry1 = new FeedEntry();
        entry1.setId("1");
        entry1.setPublished(DateUtils.parseDate("2017-01-01T12:12:12", "yyyy-MM-dd'T'HH:mm:ss"));

        FeedEntry entry2 = new FeedEntry();
        entry2.setId("2");
        entry2.setPublished(DateUtils.parseDate("2017-01-01T12:12:13", "yyyy-MM-dd'T'HH:mm:ss"));

        FeedEntry entry3 = new FeedEntry();
        entry3.setId("3");
        entry3.setPublished(DateUtils.parseDate("2017-01-01T12:12:14", "yyyy-MM-dd'T'HH:mm:ss"));

        Map<String, List<FeedEntry>> feeds = new HashMap<>();
        List<FeedEntry> entries1 = new ArrayList<>();
        entries1.add(entry1);
        entries1.add(entry3);

        feeds.put("1", entries1);

        List<FeedEntry> entries2 = new ArrayList<>();
        entries2.add(entry2);

        feeds.put("2", entries2);

        // when
        Map<String, List<FeedEntry>> newest = FeedUtils.getNewest(feeds, 2);

        // then
        Assert.assertEquals(2, newest.size());
        Assert.assertEquals(1, newest.get("1").size());
        Assert.assertEquals(1, newest.get("2").size());

        Assert.assertEquals("2", newest.get("2").get(0).getId());
        Assert.assertEquals("3", newest.get("1").get(0).getId());
    }
}
