package io.netifi.proteus;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.netifi.proteus.rsocket.ProteusSocket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.opentracing.Tracer;
import io.rsocket.Closeable;
import io.rsocket.rpc.RSocketRpcService;
import io.rsocket.rpc.rsocket.RequestHandlingRSocket;
import io.rsocket.transport.ClientTransport;
import io.rsocket.transport.netty.client.TcpClientTransport;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoProcessor;
import reactor.netty.tcp.TcpClient;

/** This is where the magic happens */
public class Proteus implements Closeable {
  private static final Logger logger = LoggerFactory.getLogger(Proteus.class);

  static {
    // Set the Java DNS cache to 60 seconds
    java.security.Security.setProperty("networkaddress.cache.ttl", "60");
  }

  private final long accesskey;
  private final String group;
  private final Tags tags;
  private final ProteusBrokerService brokerService;
  private MonoProcessor<Void> onClose;
  private RequestHandlingRSocket requestHandlingRSocket;

  private Proteus(
      long accessKey,
      ByteBuf accessToken,
      String group,
      String destination,
      Tags tags,
      boolean keepalive,
      long tickPeriodSeconds,
      long ackTimeoutSeconds,
      int missedAcks,
      List<SocketAddress> seedAddresses,
      Function<SocketAddress, ClientTransport> clientTransportFactory,
      int poolSize,
      Supplier<Tracer> tracerSupplier) {
    this.accesskey = accessKey;
    this.group = group;
    if (destination != null) {
      this.tags = tags.and("group", group, "destination", destination);
    } else {
      this.tags = tags.and("group", group);
    }

    this.onClose = MonoProcessor.create();
    this.requestHandlingRSocket = new RequestHandlingRSocket();
    this.brokerService =
        new DefaultProteusBrokerService(
            seedAddresses,
            requestHandlingRSocket,
            clientTransportFactory,
            poolSize,
            keepalive,
            tickPeriodSeconds,
            ackTimeoutSeconds,
            missedAcks,
            accessKey,
            accessToken,
            tags,
            tracerSupplier.get());
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public void dispose() {
    requestHandlingRSocket.dispose();
    onClose.onComplete();
  }

  @Override
  public boolean isDisposed() {
    return onClose.isDisposed();
  }

  @Override
  public Mono<Void> onClose() {
    return onClose;
  }

  public Proteus addService(RSocketRpcService service) {
    requestHandlingRSocket.addService(service);
    return this;
  }

  @Deprecated
  public ProteusSocket destination(String destination, String group) {
    return brokerService.unicast(Tags.of("destination", destination, "group", group));
  }

  @Deprecated
  public ProteusSocket group(String group) {
    return brokerService.unicast(Tags.of("group", group));
  }

  public ProteusSocket unicast(Tags tags) {
    return brokerService.unicast(tags);
  }

  public ProteusSocket broadcast(Tags tags) {
    return brokerService.broadcast(tags);
  }

  public ProteusSocket broadcast(ByteBuf shardKey, Tags tags) {
    return brokerService.shard(shardKey, tags);
  }

  public long getAccesskey() {
    return accesskey;
  }

  public String getGroupName() {
    return group;
  }

  public Tags getTags() {
    return tags;
  }

  public static class Builder {
    private String host = DefaultBuilderConfig.getHost();
    private Integer port = DefaultBuilderConfig.getPort();
    private List<SocketAddress> seedAddresses = DefaultBuilderConfig.getSeedAddress();
    private Long accessKey = DefaultBuilderConfig.getAccessKey();
    private String group = DefaultBuilderConfig.getGroup();
    private String destination = DefaultBuilderConfig.getDestination();
    private Tags tags = DefaultBuilderConfig.getTags();
    private String accessToken = DefaultBuilderConfig.getAccessToken();
    private byte[] accessTokenBytes = new byte[20];
    private boolean sslDisabled = DefaultBuilderConfig.isSslDisabled();
    private boolean keepalive = DefaultBuilderConfig.getKeepAlive();
    private long tickPeriodSeconds = DefaultBuilderConfig.getTickPeriodSeconds();
    private long ackTimeoutSeconds = DefaultBuilderConfig.getAckTimeoutSeconds();
    private int missedAcks = DefaultBuilderConfig.getMissedAcks();

    private Function<SocketAddress, ClientTransport> clientTransportFactory = null;
    private int poolSize = Runtime.getRuntime().availableProcessors();
    private Supplier<Tracer> tracerSupplier = () -> null;

    public Builder clientTransportFactory(
        Function<SocketAddress, ClientTransport> clientTransportFactory) {
      this.clientTransportFactory = clientTransportFactory;
      return this;
    }

    public Builder poolSize(int poolSize) {
      this.poolSize = poolSize;
      return this;
    }

    public Builder sslDisabled(boolean sslDisabled) {
      this.sslDisabled = sslDisabled;
      return this;
    }

    public Builder keepalive(boolean useKeepAlive) {
      this.keepalive = useKeepAlive;
      return this;
    }

    public Builder tickPeriodSeconds(long tickPeriodSeconds) {
      this.tickPeriodSeconds = tickPeriodSeconds;
      return this;
    }

    public Builder ackTimeoutSeconds(long ackTimeoutSeconds) {
      this.ackTimeoutSeconds = ackTimeoutSeconds;
      return this;
    }

    public Builder missedAcks(int missedAcks) {
      this.missedAcks = missedAcks;
      return this;
    }

    public Builder host(String host) {
      this.host = host;
      return this;
    }

    public Builder port(int port) {
      this.port = port;
      return this;
    }

    public Builder tracerSupplier(Supplier<Tracer> tracerSupplier) {
      this.tracerSupplier = tracerSupplier;
      return this;
    }

    public Builder seedAddresses(Collection<SocketAddress> addresses) {
      if (addresses instanceof List) {
        this.seedAddresses = (List<SocketAddress>) addresses;
      } else {
        this.seedAddresses = new ArrayList<>(addresses);
      }

      return this;
    }

    public Builder seedAddresses(SocketAddress address, SocketAddress... addresses) {
      List<SocketAddress> list = new ArrayList<>();
      list.add(address);

      if (addresses != null) {
        list.addAll(Arrays.asList(addresses));
      }

      return seedAddresses(list);
    }

    public Builder accessKey(long accessKey) {
      this.accessKey = accessKey;
      return this;
    }

    public Builder accessToken(String accessToken) {
      this.accessToken = accessToken;
      return this;
    }

    public Builder group(String group) {
      this.group = group;
      return this;
    }

    public Builder destination(String destination) {
      this.destination = destination;
      return this;
    }

    public Builder tag(String key, String value) {
      this.tags = tags.and(key, value);
      return this;
    }

    public Builder tags(String... tags) {
      this.tags = this.tags.and(tags);
      return this;
    }

    public Builder tags(Iterable<Tag> tags) {
      this.tags = this.tags.and(tags);
      return this;
    }

    public Proteus build() {
      Objects.requireNonNull(accessKey, "account key is required");
      Objects.requireNonNull(accessToken, "account token is required");
      Objects.requireNonNull(group, "group is required");

      if (clientTransportFactory == null) {
        logger.info("Client transport factory not provided; using TCP transport.");
        if (sslDisabled) {
          clientTransportFactory =
              address -> {
                TcpClient client = TcpClient.create().addressSupplier(() -> address);
                return TcpClientTransport.create(client);
              };
        } else {
          try {
            final SslProvider sslProvider;
            if (OpenSsl.isAvailable()) {
              logger.info("Native SSL provider is available; will use native provider.");
              sslProvider = SslProvider.OPENSSL_REFCNT;
            } else {
              logger.info("Native SSL provider not available; will use JDK SSL provider.");
              sslProvider = SslProvider.JDK;
            }
            final SslContext sslContext =
                SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .sslProvider(sslProvider)
                    .build();
            clientTransportFactory =
                address -> {
                  TcpClient client =
                      TcpClient.create().addressSupplier(() -> address).secure(sslContext);
                  return TcpClientTransport.create(client);
                };
          } catch (Exception sslException) {
            throw Exceptions.bubble(sslException);
          }
        }
      }

      this.accessTokenBytes = Base64.getDecoder().decode(accessToken);

      List<SocketAddress> socketAddresses;
      if (seedAddresses == null) {
        Objects.requireNonNull(host, "host is required");
        Objects.requireNonNull(port, "port is required");
        socketAddresses = Collections.singletonList(InetSocketAddress.createUnresolved(host, port));
      } else {
        socketAddresses = seedAddresses;
      }

      logger.info("registering proteus with tags {}", tags);

      return new Proteus(
          accessKey,
          Unpooled.wrappedBuffer(accessTokenBytes),
          group,
          destination,
          tags,
          keepalive,
          tickPeriodSeconds,
          ackTimeoutSeconds,
          missedAcks,
          socketAddresses,
          clientTransportFactory,
          poolSize,
          tracerSupplier);
    }
  }
}
