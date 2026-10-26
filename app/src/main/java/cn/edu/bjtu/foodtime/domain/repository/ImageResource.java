package cn.edu.bjtu.foodtime.domain.repository;

/** Bytes are reusable only when authorized by the server's current cache policy. */
public record ImageResource(byte[] bytes, boolean reusable, long freshUntil, String version) {}
