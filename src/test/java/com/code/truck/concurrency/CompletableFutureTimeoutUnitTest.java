package com.code.truck.concurrency;

import org.junit.jupiter.api.*;
import org.mockserver.integration.ClientAndServer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.*;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CompletableFutureTimeoutUnitTest {

    private ClientAndServer mockServer;
    private ScheduledExecutorService executorService;
    private static final int DEFAULT_TIMEOUT = 5;
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

        // Define MockServer expectation
        mockServer.when(
                request().withMethod("GET").withPath("/api/dummy")
        ).respond(
                response()
                        .withDelay(org.mockserver.model.Delay.seconds(30))  // Simulate delay
                        .withBody(RESULT_INSCRIPTION)
        );

        executorService = Executors.newScheduledThreadPool(1);
    }

    @AfterAll
    void tearDown() {
        executorService.shutdown();
        mockServer.stop();
    }

    private CompletableFuture<String> fetchInscriptionData() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = new URI("http://localhost:1080/api/dummy").toURL();
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

}
