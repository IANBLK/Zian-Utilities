package com.zianblk.zianutilities.core.breeding;

import java.util.*;

/** Durable snapshots; the child's values never depend on later parent/config changes. */
public record BreedingJob(
    UUID id,
    UUID owner,
    UUID first,
    UUID second,
    Child child,
    long startedAt,
    long baseMillis,
    long minimumMillis,
    long reductionMillis,
    long observedAt,
    Status status,
    String reason,
    List<Purchase> purchases,
    long readyOverrideAt) {
  /** Compatibility constructor for existing jobs and callers; zero means no admin override. */
  public BreedingJob(
      UUID id,
      UUID owner,
      UUID first,
      UUID second,
      Child child,
      long startedAt,
      long baseMillis,
      long minimumMillis,
      long reductionMillis,
      long observedAt,
      Status status,
      String reason,
      List<Purchase> purchases) {
    this(
        id,
        owner,
        first,
        second,
        child,
        startedAt,
        baseMillis,
        minimumMillis,
        reductionMillis,
        observedAt,
        status,
        reason,
        purchases,
        0);
  }

  public BreedingJob completeNow(long now, String operator) {
    if (status != Status.ACTIVE && status != Status.BLOCKED)
      throw new IllegalStateException(
          "Solo se pueden adelantar crianzas en espera, sin cobros ni entregas inciertos");
    if (now <= 0 || now < startedAt) throw new IllegalArgumentException("Invalid override clock");
    return new BreedingJob(
        id,
        owner,
        first,
        second,
        child,
        startedAt,
        baseMillis,
        minimumMillis,
        reductionMillis,
        Math.max(observedAt, now),
        Status.ACTIVE,
        "Admin complete: " + operator,
        purchases,
        now);
  }

  public enum Status {
    ACTIVE,
    ACCELERATING,
    DELIVERING,
    DELIVERED,
    BLOCKED,
    UNCERTAIN,
    CANCELLED
  }

  public enum Payment {
    PENDING,
    APPLIED,
    REJECTED,
    UNCERTAIN
  }

  public record Child(
      int schema,
      UUID uuid,
      String species,
      String form,
      String properties,
      List<Integer> ivs,
      String nature,
      String ability,
      String gender,
      String ball,
      boolean shiny,
      boolean alpha,
      float scale) {
    public Child {
      ivs = List.copyOf(ivs);
      if (schema != 1
          || uuid == null
          || species == null
          || species.isBlank()
          || form == null
          || nature == null
          || ability == null
          || gender == null
          || ball == null
          || properties == null
          || properties.isBlank()
          || properties.length() > 65536
          || ivs.size() != 6
          || ivs.stream().anyMatch(i -> i < 0 || i > 31)
          || !Float.isFinite(scale)
          || scale <= 0) throw new IllegalArgumentException("Invalid child");
    }
  }

  public record Purchase(
      UUID id, String currency, long units, long rateMillis, long reductionMillis, Payment result) {
    public Purchase {
      if (result == null
          || id == null
          || currency == null
          || currency.isBlank()
          || units <= 0
          || rateMillis <= 0
          || reductionMillis <= 0) throw new IllegalArgumentException("Invalid purchase");
    }
  }

  public BreedingJob {
    purchases = List.copyOf(purchases);
    if (readyOverrideAt < 0 || (readyOverrideAt > 0 && readyOverrideAt < startedAt))
      throw new IllegalArgumentException("Invalid admin override");
    if (id == null
        || owner == null
        || first == null
        || second == null
        || first.equals(second)
        || child == null
        || child.uuid().equals(first)
        || child.uuid().equals(second)
        || child.uuid().equals(id)
        || status == null) throw new IllegalArgumentException("Invalid identities");
    BreedingRules.readyAt(startedAt, baseMillis, minimumMillis, reductionMillis);
    if (purchases.stream().map(Purchase::id).distinct().count() != purchases.size())
      throw new IllegalArgumentException("Duplicate purchase");
  }

  public boolean occupiesSlot() {
    return status != Status.DELIVERED && status != Status.CANCELLED;
  }

  public long readyAt() {
    return readyOverrideAt > 0
        ? Math.min(
            readyOverrideAt,
            BreedingRules.readyAt(startedAt, baseMillis, minimumMillis, reductionMillis))
        : BreedingRules.readyAt(startedAt, baseMillis, minimumMillis, reductionMillis);
  }

  public BreedingJob phase(Status next, String why, long now) {
    return new BreedingJob(
        id,
        owner,
        first,
        second,
        child,
        startedAt,
        baseMillis,
        minimumMillis,
        reductionMillis,
        Math.max(observedAt, now),
        next,
        why,
        purchases,
        readyOverrideAt);
  }

  public BreedingJob begin(Purchase purchase, long now) {
    if (status != Status.ACTIVE
        || readyOverrideAt > 0
        || purchases.stream().anyMatch(p -> p.id().equals(purchase.id())))
      throw new IllegalStateException("Operation not available");
    var list = new ArrayList<>(purchases);
    list.add(purchase);
    return new BreedingJob(
        id,
        owner,
        first,
        second,
        child,
        startedAt,
        baseMillis,
        minimumMillis,
        reductionMillis,
        Math.max(observedAt, now),
        Status.ACCELERATING,
        "",
        list,
        readyOverrideAt);
  }

  public BreedingJob payment(Payment result, long now) {
    if (result == null || result == Payment.PENDING)
      throw new IllegalArgumentException("Payment result required");
    if (status != Status.ACCELERATING && status != Status.UNCERTAIN)
      throw new IllegalStateException("No payment pending");
    var list = new ArrayList<>(purchases);
    var pending = list.removeLast();
    if (pending.result() != Payment.PENDING && pending.result() != Payment.UNCERTAIN)
      throw new IllegalStateException("Payment already closed");
    list.add(
        new Purchase(
            pending.id(),
            pending.currency(),
            pending.units(),
            pending.rateMillis(),
            pending.reductionMillis(),
            result));
    return new BreedingJob(
        id,
        owner,
        first,
        second,
        child,
        startedAt,
        baseMillis,
        minimumMillis,
        result == Payment.APPLIED
            ? Math.addExact(reductionMillis, pending.reductionMillis())
            : reductionMillis,
        Math.max(observedAt, now),
        result == Payment.UNCERTAIN ? Status.UNCERTAIN : Status.ACTIVE,
        result == Payment.UNCERTAIN ? "PAYMENT" : "",
        list,
        readyOverrideAt);
  }
}
