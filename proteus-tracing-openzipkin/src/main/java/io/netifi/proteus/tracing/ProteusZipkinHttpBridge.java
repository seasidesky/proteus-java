package io.netifi.proteus.tracing;

import com.google.protobuf.InvalidProtocolBufferException;
import io.netifi.proteus.Proteus;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelOption;
import java.time.Duration;
import java.util.Optional;
import java.util.StringJoiner;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufFlux;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import zipkin2.proto3.Span;

public class ProteusZipkinHttpBridge implements ProteusTracingService {
  private static final Logger logger = LoggerFactory.getLogger(ProteusZipkinHttpBridge.class);

  private static final String DEFAULT_ZIPKIN_SPANS_URL = "/api/v2/spans";
  private static final String DEFAULT_ZIPKIN_TRACES_URL = "/api/v2/traces";

  private final String host;
  private final int port;
  private final String zipkinSpansUrl;
  private HttpClient httpClient;

  public ProteusZipkinHttpBridge(String host, int port, String zipkinSpansUrl) {
    this.host = host;
    this.port = port;
    this.zipkinSpansUrl = zipkinSpansUrl;
  }

  public ProteusZipkinHttpBridge(String host, int port) {
    this(host, port, DEFAULT_ZIPKIN_SPANS_URL);
  }

  public static void main(String... args) {
    logger.info("Starting Stand-alone Proteus Zipkin HTTP Bridge");

    String group = System.getProperty("netifi.tracingGroup", "com.netifi.proteus.tracing");
    String brokerHost = System.getProperty("netifi.proteus.host", "localhost");
    int brokerPort = Integer.getInteger("netifi.proteus.port", 8001);
    String zipkinHost = System.getProperty("netifi.proteus.zipkinHost", "localhost");
    int zipkinPort = Integer.getInteger("netifi.proteus.zipkinPort", 9411);
    String zipkinSpansUrl =
        System.getProperty("netifi.proteus.zipkinSpansUrl", DEFAULT_ZIPKIN_SPANS_URL);
    long accessKey = Long.getLong("netifi.proteus.accessKey", 3855261330795754807L);
    String accessToken =
        System.getProperty("netifi.authentication.accessToken", "kTBDVtfRBO4tHOnZzSyY5ym2kfY");

    logger.info("group - {}", group);
    logger.info("broker host - {}", brokerHost);
    logger.info("broker port - {}", brokerPort);
    logger.info("zipkin host - {}", zipkinHost);
    logger.info("zipkin port - {}", zipkinPort);
    logger.info("zipkin spans url - {}", zipkinSpansUrl);
    logger.info("access key - {}", accessKey);

    Proteus proteus =
        Proteus.builder()
            .accessKey(accessKey)
            .accessToken(accessToken)
            .group(group)
            .host(brokerHost)
            .port(brokerPort)
            .destination("standaloneZipkinBridge")
            .build();

    proteus.addService(
        new ProteusTracingServiceServer(
            new ProteusZipkinHttpBridge(zipkinHost, zipkinPort, zipkinSpansUrl),
            Optional.empty(),
            Optional.empty()));

    proteus.onClose().block();
  }

  private synchronized HttpClient getClient() {
    if (httpClient == null) {
      this.httpClient =
          HttpClient.create(ConnectionProvider.fixed("proteusZipkinBridge"))
              .compress(true)
              .port(port)
              .tcpConfiguration(
                  tcpClient ->
                      tcpClient
                          .host(host)
                          .option(ChannelOption.SO_KEEPALIVE, true)
                          .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30_000));
    }
    return httpClient;
  }

  private synchronized void resetHttpClient() {
    this.httpClient = null;
  }

  @Override
  public Mono<Ack> streamSpans(Publisher<Span> messages, ByteBuf metadata) {
    return Flux.from(messages)
        .map(
            span -> {
              try {
                String json = JsonFormat.printer().print(span);
                if (logger.isTraceEnabled()) {
                  logger.trace("receiving tracing data {}", json);
                }
                return json;
              } catch (InvalidProtocolBufferException e) {
                throw Exceptions.propagate(e);
              }
            })
        .windowTimeout(128, Duration.ofMillis(1000))
        .map(
            strings ->
                strings
                    .reduce(new StringJoiner(","), StringJoiner::add)
                    .map(stringJoiner -> "[" + stringJoiner.toString() + "]"))
        .onBackpressureBuffer(1 << 16)
        .flatMap(stringMono -> stringMono)
        .concatMap(
            spans ->
                getClient()
                    .headers(hh -> hh.add("Content-Type", "application/json"))
                    .post()
                    .uri(zipkinSpansUrl)
                    .send(ByteBufFlux.fromString(Mono.just(spans)))
                    .response()
                    .timeout(Duration.ofSeconds(30))
                    .doOnError(throwable -> resetHttpClient()),
            8)
        .doOnError(
            throwable ->
                logger.error(
                    "error sending data to tracing data to url " + zipkinSpansUrl, throwable))
        .then(Mono.never());
  }
}
