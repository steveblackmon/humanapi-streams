package co.humanapi.streams.provider;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.streams.jackson.StreamsJacksonMapper;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Scanner;

import static org.junit.Assert.assertNotNull;

/**
 *
 */
public class HumanApiSerDeTest {

    private final static Logger LOGGER = LoggerFactory.getLogger(HumanApiSerDeTest.class);

    private ObjectMapper mapper = StreamsJacksonMapper.getInstance();

    @Test
    public void Tests() {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, Boolean.TRUE);
        mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, Boolean.TRUE);
        mapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, Boolean.TRUE);

        try {

            URL testResourcesUrl = getClass().getResource("/");
            Path testResourcesPath = Paths.get(testResourcesUrl.toURI());
            assertNotNull("Test file missing",
                    testResourcesPath);

            Files.walkFileTree(testResourcesPath, new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.toString().endsWith(".json")) {
                        try {
                            Scanner scanner = null;
                            scanner = new Scanner(new FileInputStream(file.toFile()));

                            while (scanner.hasNext()) {
                                String jsonLine = scanner.nextLine();
                                JsonNode jsonNode = null;
                                try {
                                    jsonNode = mapper.readValue(jsonLine, JsonNode.class);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                LOGGER.info(mapper.writeValueAsString(jsonNode));
                            }
                        } catch (Throwable e) {
                            e.printStackTrace();
                            Assert.fail();
                        }

                    }
                    return FileVisitResult.CONTINUE;
                }
            });
//                    Scanner scanner = new Scanner(instr);
//                    while (scanner.hasNext()) {
//                        String jsonLine = scanner.nextLine();
//                        JsonNode jsonNode = mapper.readValue(jsonLine, JsonNode.class);
//                        LOGGER.info(mapper.writeValueAsString(jsonNode));
//                    }


        } catch (IOException e) {
            e.printStackTrace();
            Assert.fail("IOException: "+ e.getMessage());
        } catch (URISyntaxException e) {
            e.printStackTrace();
            Assert.fail("IOException: "+ e.getMessage());
        }

    }
}
