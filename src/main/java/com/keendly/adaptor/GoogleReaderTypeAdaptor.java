package com.keendly.adaptor;

import static com.keendly.utils.JsonUtils.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.keendly.adaptor.model.FeedEntry;
import com.keendly.adaptor.model.auth.Token;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class GoogleReaderTypeAdaptor extends Adaptor {

    protected GoogleReaderTypeAdaptor(Token token) {
        super(token);
    }
    protected GoogleReaderTypeAdaptor() {
    }

    protected abstract Response get(String url);

    protected String normalizeFeedId(String feedId){
        return feedId;
    }

    @Override
    public Map<String, List<FeedEntry>> getUnread(List<String> feedIds) {
        Map<String, Integer> unreadCounts = getUnreadCount(feedIds);
        Map<String, List<FeedEntry>> unreads = new HashMap<String, List<FeedEntry>>();
        for (Map.Entry<String, Integer> entry : unreadCounts.entrySet()){
            List<FeedEntry> unread = doGetUnread(entry.getKey(), entry.getValue(), null);
            unreads.put(entry.getKey(), unread);
        }
        return unreads;
    }

    private List<FeedEntry> doGetUnread(String feedId, int unreadCount, String continuation){
        //int count = unreadCount > MAX_ARTICLES_PER_FEED ? MAX_ARTICLES_PER_FEED : unreadCount; // TODO inform user
        int count = unreadCount;
        String url ="/stream/contents/" +normalizeFeedId(feedId) + "?xt=user/-/state/com.google/read";
        url = continuation == null ? url : url + "&c=" + continuation;
        Response response = get(url);
        JsonNode jsonResponse = asJson(response);
        JsonNode items = jsonResponse.get("items");
        if (items == null){
            return Collections.emptyList();
        }
        List<FeedEntry> entries = new ArrayList();
        for (JsonNode item : items){
            FeedEntry entry = toFeedEntry(item);
            entries.add(entry);
        }
        List<FeedEntry> ret = new ArrayList();
        ret.addAll(entries);
        if (ret.size() < count){
            JsonNode continuationNode = jsonResponse.get("continuation");
            boolean hasContinuation = continuationNode != null;
            if (hasContinuation){
                List<FeedEntry> nextPage =
                        doGetUnread(feedId, count - ret.size(), continuationNode.asText());
                ret.addAll(nextPage);
                return ret;
            }
        }

        return ret;
    }

    protected static FeedEntry toFeedEntry(JsonNode item){
        FeedEntry entry = new FeedEntry();
        entry.setUrl(GoogleReaderMapper.extractArticleUrl(item));
        entry.setId(asText(item, "id"));
        entry.setTitle(asText(item, "title"));
        entry.setAuthor(asText(item, "author"));
        entry.setPublished(asDate(item, "published"));
        entry.setContent(GoogleReaderMapper.extractContent(item));
        return entry;
    }
}
