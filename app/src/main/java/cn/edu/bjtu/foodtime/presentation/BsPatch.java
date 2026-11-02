package cn.edu.bjtu.foodtime.presentation;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Applies a bsdiff(1) binary patch ("BSDIFF40") to the installed APK.
 * Works file-to-file so a 50MB APK is never fully buffered in memory.
 * The output must match the published APK byte-for-byte, which the caller verifies by SHA-256.
 */
final class BsPatch {
  private static final byte[] MAGIC = "BSDIFF40".getBytes(StandardCharsets.US_ASCII);
  private static final long MAX_OUTPUT = 200L * 1024 * 1024;
  private static final int BUFFER = 64 * 1024;

  private BsPatch() { }

  static void apply(File oldApk, File patchFile, File output) throws IOException {
    try (var patch = new RandomAccessFile(patchFile, "r")) {
      if (patch.length() < 32) throw new IOException("补丁文件不完整");
      byte[] header = new byte[32];
      patch.readFully(header);
      for (int i = 0; i < MAGIC.length; i++) {
        if (header[i] != MAGIC[i]) throw new IOException("补丁格式不正确");
      }
      long controlLength = offset(header, 8);
      long diffLength = offset(header, 16);
      long newSize = offset(header, 24);
      if (controlLength < 0 || diffLength < 0 || newSize < 0 || newSize > MAX_OUTPUT) {
        throw new IOException("补丁头部无效");
      }
      long controlStart = 32;
      long diffStart = controlStart + controlLength;
      long extraStart = diffStart + diffLength;
      if (diffStart < controlStart || extraStart < diffStart || extraStart > patch.length()) {
        throw new IOException("补丁长度超出文件");
      }
      try (var old = new RandomAccessFile(oldApk, "r");
           var control = compressedAt(patchFile, controlStart);
           var diff = compressedAt(patchFile, diffStart);
           var extra = compressedAt(patchFile, extraStart);
           var out = new BufferedOutputStream(new FileOutputStream(output))) {
        byte[] block = new byte[24];
        byte[] base = new byte[BUFFER];
        byte[] copied = new byte[BUFFER];
        long oldPosition = 0, newPosition = 0;
        while (newPosition < newSize) {
          checkCancelled();
          readFully(control, block);
          long addLength = offset(block, 0);
          long extraLength = offset(block, 8);
          long seek = offset(block, 16);
          if (addLength < 0 || extraLength < 0 || addLength > newSize - newPosition
              || extraLength > newSize - newPosition - addLength) {
            throw new IOException("补丁控制段无效");
          }
          long produced = 0;
          while (produced < addLength) {
            int chunk = (int) Math.min(BUFFER, addLength - produced);
            Arrays.fill(base, 0, chunk, (byte) 0);
            long source = oldPosition + produced;
            if (source >= 0 && source < old.length()) {
              old.seek(source);
              old.readFully(base, 0, (int) Math.min(chunk, old.length() - source));
            }
            for (int i = 0; i < chunk; i++) {
              int value = diff.read();
              if (value < 0) throw new EOFException("补丁差分数据不完整");
              out.write(value + (base[i] & 255));
            }
            produced += chunk;
          }
          newPosition += addLength;
          oldPosition += addLength;
          produced = 0;
          while (produced < extraLength) {
            int chunk = (int) Math.min(BUFFER, extraLength - produced);
            readFully(extra, copied, chunk);
            out.write(copied, 0, chunk);
            produced += chunk;
          }
          newPosition += extraLength;
          oldPosition += seek;
        }
      }
    }
  }

  private static InputStream compressedAt(File patch, long offset) throws IOException {
    var file = new FileInputStream(patch);
    try {
      long skipped = 0;
      while (skipped < offset) {
        long step = file.skip(offset - skipped);
        if (step <= 0) throw new EOFException("补丁数据不完整");
        skipped += step;
      }
    } catch (IOException failure) {
      file.close();
      throw failure;
    }
    return new BZip2CompressorInputStream(new BufferedInputStream(file), true);
  }

  private static void readFully(InputStream stream, byte[] target) throws IOException {
    readFully(stream, target, target.length);
  }

  private static void readFully(InputStream stream, byte[] target, int length) throws IOException {
    int offset = 0;
    while (offset < length) {
      int count = stream.read(target, offset, length - offset);
      if (count < 0) throw new EOFException("补丁数据不完整");
      offset += count;
    }
  }

  private static void checkCancelled() throws IOException {
    if (Thread.currentThread().isInterrupted()) throw new IOException("更新已取消");
  }

  /** bsdiff stores signed 64-bit values as sign-magnitude little endian. */
  static long offset(byte[] data, int at) {
    long value = 0;
    for (int i = 0; i < 8; i++) value |= (long) (data[at + i] & 255) << (8 * i);
    return (value & Long.MIN_VALUE) == 0 ? value : -(value & Long.MAX_VALUE);
  }
}
