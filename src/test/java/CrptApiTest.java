import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import ru.grimashevich.CrptApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class CrptApiTest {
    private static final String HOST_URL = "localhost";
    private static final String ENDPOINT_URL = "/create_document";

    private static WireMockServer wireMockServer;
    private static CrptApi crptApi;
    private static Random random;

    @BeforeAll
    static void prepare() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor(HOST_URL, wireMockServer.port());

        wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo(ENDPOINT_URL))
                .willReturn(WireMock.aResponse().withStatus(200).withBody("Ok"))
        );

        crptApi = new CrptApi(TimeUnit.SECONDS, 42, "http://"
                + HOST_URL + ":" + wireMockServer.port() + ENDPOINT_URL);

        random = new Random();
    }

    @Test
    void CrptApiTest() throws InterruptedException, JsonProcessingException {
        List<Thread> threads = new ArrayList<>(19);
        CrptApi.Document document = new CrptApi.Document();
        document.setDocId("1");
        String documentJson = new ObjectMapper().writeValueAsString(document);

        for (int i = 0; i < 19; i++) {
            threads.add(new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    crptApi.sendDocument(document, "");
                }
            }));
            threads.get(i).start();
        }
        Thread.sleep(900);
        wireMockServer.verify(42, WireMock.postRequestedFor(WireMock.urlEqualTo(ENDPOINT_URL))
                .withRequestBody(WireMock.equalTo(documentJson)));

        for (int i = 1; i < 5; i++) {
            Thread.sleep(1000);
            wireMockServer.verify(42 * (i + 1), WireMock.postRequestedFor(WireMock.urlEqualTo(ENDPOINT_URL))
                    .withRequestBody(WireMock.equalTo(documentJson)));
        }

        for (Thread thread : threads) {
            thread.interrupt();
        }
    }


    @AfterAll
    static void shutdown() {
        wireMockServer.shutdown();
        crptApi.close();
    }
}
