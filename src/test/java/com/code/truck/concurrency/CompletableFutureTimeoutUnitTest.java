package com.code.truck.concurrency;

import org.junit.jupiter.api.*;
import org.mockserver.integration.ClientAndServer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CompletableFutureTimeoutUnitTest {

    private ClientAndServer mockServer;
    private ScheduledExecutorService executorService;
    private static final int DEFAULT_TIMEOUT = 5;
    private static final String SPECIFIC_UUID = "809bf655-26a7-46a0-8752-fc3bcc0cb890";
    private static final String RESULT_INSCRIPTION = """
            {
            "modifications": [
            {
            "id":1,
            "description":"whatever",
            "success":true
            }
            ]
            }
            """;
    private static final String DEFAULT_RESULT_INSCRIPTION = """
                        {
            "modifications": [
            {
            "id":1,
            "description":"whatever",
            "success":false
            }
            ]
            }
            """;

    @BeforeAll
    void setUp() {
        mockServer = ClientAndServer.startClientAndServer(1080);

        // Define MockServer behavior for the specific UUID (with delay)
        mockServer.when(
                request().withPath("/api/effectuer-inscription").withQueryStringParameter("uuid", SPECIFIC_UUID)
        ).respond(
                response()
                        .withDelay(org.mockserver.model.Delay.seconds(5))  // Simulate delay for this UUID
                        .withBody(RESULT_INSCRIPTION)
        );

        // Define MockServer behavior for all other UUIDs (no delay)
        mockServer.when(
                request().withPath("/api/effectuer-inscription").withQueryStringParameter("uuid", "[^" + SPECIFIC_UUID + "]")
        ).respond(
                response()
                        .withBody(RESULT_INSCRIPTION)  // Respond immediately for all other UUIDs
        );

        executorService = Executors.newScheduledThreadPool(2);
    }

    @AfterAll
    void tearDown() {
        executorService.shutdown();
        mockServer.stop();
    }

    private CompletableFuture<String> fetchInscriptionData() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = new URI("http://localhost:1080/api/effectuer-inscription").toURL();
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                try(BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String inputLine;
                    StringBuilder response = new StringBuilder();

                    while((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }

                    return response.toString();
                } finally {
                    connection.disconnect();
                }
            } catch (Exception ex) {
                return "";
            }
        });
    }

    private CompletableFuture<String> fetchInscriptionData(String uuid) {
        return CompletableFuture.supplyAsync(() -> {
            System.out.println("Traiter UUID : " + uuid);
            try {
                URL url = new URI("http://localhost:1080/api/effectuer-inscription?uuid=" + uuid).toURL();
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                try(BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String inputLine;
                    StringBuilder response = new StringBuilder();

                    while((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }

                    return response.toString();
                } finally {
                    connection.disconnect();
                }
            } catch (Exception ex) {
                return "";
            }
        });
    }

//    @Test
//    void whenOrTimeout_thenGetThrow() {
//        CompletableFuture<String> inscriptionDataFuture = fetchInscriptionData();
//        inscriptionDataFuture.orTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS);
//        Assertions.assertThrows(ExecutionException.class, inscriptionDataFuture::get);
//    }

    @Test
    void whenOrTimeout_thenGetThrow() {
        CompletableFuture<String> inscriptionDataFuture = fetchInscriptionData();
        inscriptionDataFuture.orTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS);  // Use milliseconds

        long startTime = System.currentTimeMillis();  // Record the start time

        // Expect the future to throw an exception due to timeout
        // Attempt to get the result, expecting a timeout
        Assertions.assertThrows(ExecutionException.class, inscriptionDataFuture::get);

        long endTime = System.currentTimeMillis();  // Record the end time
        System.out.println("Elapsed time: " + (endTime - startTime) + " milliseconds");  // Print elapsed time
    }

    @Test
    void forListOfCompletableFuture_whenOrTimeout_thenGetThrowInterruptingLongRunningExecution() {

        List<String> uuids = List.of(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                SPECIFIC_UUID,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString()
        );

        //Fetch inscription data for each UUID and create a list of CompletableFuture
        List<CompletableFuture<String>> inscriptionDataFutures = uuids.stream().map(this::fetchInscriptionData).toList();

        // Combine all futures into a single CompletableFuture
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                inscriptionDataFutures.toArray(new CompletableFuture[0])
        ).orTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS);

        long startTime = System.currentTimeMillis();
        // Expect the combined future to throw an exception due to timeout
        Assertions.assertThrows(ExecutionException.class, allFutures::get);
        long endTime = System.currentTimeMillis();

        System.out.println("Elapsed time: " + (endTime - startTime) + " milliseconds");  // Print elapsed time
    }

}
