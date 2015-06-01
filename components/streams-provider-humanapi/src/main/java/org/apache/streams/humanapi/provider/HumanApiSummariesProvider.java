package org.apache.streams.humanapi.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.streams.components.http.provider.SimpleHTTPGetProvider;
import org.apache.streams.core.StreamsDatum;
import org.apache.streams.core.StreamsResultSet;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Created by steve on 10/11/14.
 */
public class HumanApiSummariesProvider extends SimpleHTTPGetProvider {

    private final static Logger LOGGER = LoggerFactory.getLogger(HumanApiSummariesProvider.class);

    protected volatile Queue<StreamsDatum> providerQueue = new ConcurrentLinkedQueue<StreamsDatum>();

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /** resources */
    public static Collection<String> summaryResources = Lists.newArrayList(
        "activities",
        "sleeps"
    );

    /** Access token for current session */
    private String accessToken;

    @Override
    public void startStream() {
        LOGGER.trace("Starting Producer");

    }

    @Override
    public StreamsResultSet readCurrent() {
        StreamsResultSet current;

        List<URI> resourceRequests = summaryResources.stream().map(resource -> {
            try {
                URIBuilder builder = new URIBuilder(uriBuilder.build());
                builder = builder.setPath(
                    Joiner.on("/").skipNulls().join(builder.getPath(), resource, "summary")
                );
                return builder.build();
            } catch (URISyntaxException e) {
                e.printStackTrace();
                return null;
            }
        }).collect(Collectors.toList());

        List<ObjectNode> results = resourceRequests.parallelStream().flatMap(uri -> executeGet(uri).stream()).filter(object -> object != null).collect(Collectors.toList());

        lock.writeLock().lock();

        providerQueue.addAll(results.stream().map(item ->
            new StreamsDatum(item, item.get("id").asText(), new DateTime(item.get("date").asText()))
        ).collect(Collectors.toList()));

        LOGGER.debug("Creating new result set for {} items", providerQueue.size());
        current = new StreamsResultSet(providerQueue);

        return current;
    }

    @Override
    public StreamsResultSet readNew(BigInteger sequence) {
        return readCurrent();
    }

    @Override
    public StreamsResultSet readRange(DateTime start, DateTime end) {

        StreamsResultSet current;

        lock.writeLock().lock();
        uriBuilder.setParameter("start_date", start.toString(DateTimeFormat.forPattern("YYYY-MM-dd")));
        uriBuilder.setParameter("end_date", end.toString(DateTimeFormat.forPattern("YYYY-MM-dd")));
        int limit = 50;
        uriBuilder.setParameter("limit", Integer.toString(limit));
        int total = Integer.MIN_VALUE;
        List<ObjectNode> results = new ArrayList<>();
        for( String resource : summaryResources ) {
            int offset = 0;
            try {
                uriBuilder = uriBuilder.setPath(
                    Joiner.on("/").skipNulls().join(uriBuilder.getPath(), resource, "summaries")
                );
                URI uri =  uriBuilder.build();
                boolean done = true;
                do
                {
                    uriBuilder.setParameter("offset", Integer.toString(offset));
                    HttpGet httpget = new HttpGet(uri);
                    CloseableHttpResponse response = null;
                    String entityString = null;
                    try {
                        response = httpclient.execute(httpget);
                        HttpEntity entity = response.getEntity();
                        // TODO: handle retry
                        if (response.getStatusLine().getStatusCode() == 200 && entity != null) {
                            entityString = EntityUtils.toString(entity);
                            if (response.getStatusLine().getStatusCode() == 200 && entity != null) {

                                JsonNode entityJsonNode = mapper.readValue(entityString, JsonNode.class);
                                if (entityJsonNode != null && entityJsonNode instanceof ObjectNode)
                                    results.add((ObjectNode) entityJsonNode);
                                else if (entityJsonNode != null && entityJsonNode instanceof ArrayNode) {
                                    ((ArrayNode) entityJsonNode).elements().forEachRemaining(obj -> results.add((ObjectNode) obj));
                                }
                                Header[] headers = response.getAllHeaders();
                                for (Header header : headers) {
                                    if (header.getName().equals("X-Total-Count")) {
                                        total = Integer.parseInt(header.getValue());
                                    }
                                }
                                if (total > (offset + limit)) {
                                    offset += limit;
                                    done = false;
                                } else {
                                    done = true;
                                }
                            } else {
                                done = true;
                            }

                        }
                    } catch (IOException e) {
                        LOGGER.error("IO error:\n{}\n{}\n{}", uri.toString(), response, e.getMessage());
                        return null;
                    } finally {
                        try {
                            response.close();
                        } catch (IOException e) {}
                    }
                } while (done == false);
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }

        providerQueue.addAll(results.stream().map(item ->
                        new StreamsDatum(item, item.get("id").asText(), new DateTime(item.get("date").asText()))
        ).collect(Collectors.toList()));

        LOGGER.debug("Creating new result set for {} items", providerQueue.size());
        current = new StreamsResultSet(providerQueue);

        return current;

    }

    @Override
    public boolean isRunning() {
        return true;
    }

    @Override
    public void prepare(Object configurationObject) {
        super.prepare(configurationObject);
        Preconditions.checkNotNull(configuration);
        accessToken = configuration.getAccessToken();
        Preconditions.checkNotNull(accessToken);
        uriBuilder = uriBuilder.addParameter("access_token", accessToken);
    }
}
