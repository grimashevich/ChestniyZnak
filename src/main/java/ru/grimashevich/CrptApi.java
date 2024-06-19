package ru.grimashevich;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class CrptApi implements AutoCloseable {

    private final URI uri;
    private final Semaphore semaphore;
    private final ScheduledExecutorService resetCounterScheduler;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Logger logger;


    public CrptApi(TimeUnit timeUnit, int requestLimit, String endpointUrl) {
        this.semaphore = new Semaphore(requestLimit);
        this.resetCounterScheduler = Executors.newSingleThreadScheduledExecutor();
        objectMapper = new ObjectMapper();
        httpClient = HttpClient.newHttpClient();
        uri = URI.create(endpointUrl);
        logger = Logger.getLogger(this.getClass().getName());
        resetCounterScheduler.scheduleAtFixedRate(() -> semaphore.release(requestLimit), 1, 1, timeUnit);
    }

    public void sendDocument(Document document, String signature) {
        try {
            String jsonDocument = objectMapper.writeValueAsString(document);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Content-Type", "application/json")
                    .header("Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonDocument))
                    .build();

            semaphore.acquire();
            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            String responseResultMsg = String.format("Документ с id %s обработан со статусом %d", document.getDocId(),
                    httpResponse.statusCode());
            if (httpResponse.statusCode() / 100 == 2) {
                logger.info(responseResultMsg);
            } else {
                logger.warning(responseResultMsg + ": " + httpResponse.body());
            }

        } catch (InterruptedException ignored) {
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        resetCounterScheduler.shutdown();
    }

    @Data
    public static class Document {
        private String docId;
    }


}
