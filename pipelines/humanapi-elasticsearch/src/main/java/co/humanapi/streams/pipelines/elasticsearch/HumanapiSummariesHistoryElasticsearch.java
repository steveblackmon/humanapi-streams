package co.humanapi.streams.pipelines.elasticsearch;

import com.google.common.collect.Maps;
import com.typesafe.config.Config;
import org.apache.streams.config.StreamsConfigurator;
import org.apache.streams.core.StreamBuilder;
import org.apache.streams.elasticsearch.ElasticsearchConfigurator;
import org.apache.streams.elasticsearch.ElasticsearchPersistWriter;
import org.apache.streams.elasticsearch.ElasticsearchWriterConfiguration;
import org.apache.streams.humanapi.provider.HumanApiSummariesProvider;
import org.apache.streams.local.builders.LocalStreamBuilder;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Created by sblackmon on 12/10/13.
 */
public class HumanapiSummariesHistoryElasticsearch {

    private final static Logger LOGGER = LoggerFactory.getLogger(HumanapiSummariesHistoryElasticsearch.class);

    public static void main(String[] args)
    {
        LOGGER.info(StreamsConfigurator.config.toString());

        Config elasticsearch = StreamsConfigurator.config.getConfig("elasticsearch");

        ElasticsearchWriterConfiguration elasticsearchWriterConfiguration = ElasticsearchConfigurator.detectWriterConfiguration(elasticsearch);

        Map<String, Object> streamConfig = Maps.newHashMap();
        streamConfig.put(LocalStreamBuilder.TIMEOUT_KEY, 20 * 60 * 1000);

        StreamBuilder builder = new LocalStreamBuilder(100, streamConfig);

        HumanApiSummariesProvider summaries = new HumanApiSummariesProvider();

        builder.newReadRangeStream("summaries", summaries, new DateTime().withYear(2000).withMonthOfYear(1).withDayOfMonth(1), new DateTime().withYear(2015).withMonthOfYear(1).withDayOfMonth(1));
        builder.addStreamsPersistWriter("summaries-es", new ElasticsearchPersistWriter(elasticsearchWriterConfiguration), 1, "summaries");
        builder.start();

    }
}
