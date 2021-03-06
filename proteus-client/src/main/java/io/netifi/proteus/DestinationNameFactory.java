package io.netifi.proteus;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Generates names for a Destination when connection to the Proteus Proteus platform */
public interface DestinationNameFactory extends Supplier<String> {
  static DestinationNameFactory from(String destination) {
    return () -> destination;
  }

  static CountingDestinationNameFactory from(String destination, AtomicInteger counter) {
    return new CountingDestinationNameFactory(destination, counter);
  }

  /**
   * Releases the name back to the factory so it can be used again.
   *
   * @param name destination name returned to factory
   */
  default void release(String name) {}

  /**
   * Looks and see what the next name in line is going to be
   *
   * @return the next name generated by the factory
   */
  default String peek() {
    return get();
  }

  default String rootName() {
    return get();
  }

  class CountingDestinationNameFactory implements DestinationNameFactory {
    private String destination;
    private AtomicInteger counter;

    private CountingDestinationNameFactory(String destination, AtomicInteger counter) {
      this.destination = destination;
      this.counter = counter;
    }

    @Override
    public String rootName() {
      return destination;
    }

    @Override
    public String get() {
      return destination + "-" + counter.getAndIncrement();
    }

    public void release(String name) {
      counter.decrementAndGet();
    }

    @Override
    public String peek() {
      return destination + "-" + counter.get();
    }
  }
}
