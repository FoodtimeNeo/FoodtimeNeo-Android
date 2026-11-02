package cn.edu.bjtu.foodtime.presentation;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

public class BsPatchTest {
  @Test
  public void appliesADiffOnlyPatch() throws Exception {
    byte[] oldBytes = pattern(1024, 17);
    byte[] newBytes = pattern(1024, 23);
    byte[] patch = patch(newBytes.length, diff(oldBytes, newBytes), new byte[0]);
    File output = File.createTempFile("bspatch", ".out");
    try {
      BsPatch.apply(writeTemp(oldBytes), writeTemp(patch), output);
      assertArrayEquals(newBytes, Files.readAllBytes(output.toPath()));
    } finally {
      output.delete();
    }
  }

  @Test
  public void appliesADiffPlusExtraPatch() throws Exception {
    byte[] oldBytes = pattern(512, 5);
    byte[] prefix = pattern(512, 9);
    byte[] appended = "appended-data".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    byte[] expected = new byte[prefix.length + appended.length];
    System.arraycopy(prefix, 0, expected, 0, prefix.length);
    System.arraycopy(appended, 0, expected, prefix.length, appended.length);
    byte[] patch = patch(expected.length, diff(oldBytes, prefix), appended);
    File output = File.createTempFile("bspatch", ".out");
    try {
      BsPatch.apply(writeTemp(oldBytes), writeTemp(patch), output);
      assertArrayEquals(expected, Files.readAllBytes(output.toPath()));
    } finally {
      output.delete();
    }
  }

  @Test
  public void rejectsCorruptHeaderAndTruncatedData() throws Exception {
    File output = File.createTempFile("bspatch", ".out");
    try {
      assertThrows(IOException.class, () -> BsPatch.apply(writeTemp(new byte[1]), writeTemp(new byte[8]), output));
      byte[] valid = patch(64, diff(pattern(64, 1), pattern(64, 2)), new byte[0]);
      byte[] truncated = java.util.Arrays.copyOf(valid, valid.length / 2);
      assertThrows(IOException.class, () -> BsPatch.apply(writeTemp(pattern(64, 1)), writeTemp(truncated), output));
    } finally {
      output.delete();
    }
  }

  @Test
  public void decodesSignMagnitudeOffsets() {
    byte[] value = new byte[8];
    value[0] = 5;
    org.junit.Assert.assertEquals(5, BsPatch.offset(value, 0));
    value[7] = (byte) 0x80;
    org.junit.Assert.assertEquals(-5, BsPatch.offset(value, 0));
  }

  private static File writeTemp(byte[] content) throws IOException {
    File file = File.createTempFile("bspatch", ".in");
    Files.write(file.toPath(), content);
    file.deleteOnExit();
    return file;
  }

  private static byte[] diff(byte[] oldBytes, byte[] next) {
    byte[] result = new byte[Math.min(oldBytes.length, next.length)];
    for (int i = 0; i < result.length; i++) result[i] = (byte) (next[i] - oldBytes[i]);
    return result;
  }

  private static byte[] pattern(int size, int seed) {
    byte[] value = new byte[size];
    for (int i = 0; i < size; i++) value[i] = (byte) (i * seed + i / 7);
    return value;
  }

  private static byte[] patch(long newSize, byte[] diff, byte[] extra) throws IOException {
    var control = new ByteArrayOutputStream();
    control.write(offset(diff.length));
    control.write(offset(extra.length));
    control.write(offset(0));
    byte[] controlBlock = compress(control.toByteArray());
    byte[] diffBlock = compress(diff);
    byte[] extraBlock = compress(extra);
    var patch = new ByteArrayOutputStream();
    patch.write("BSDIFF40".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    patch.write(offset(controlBlock.length));
    patch.write(offset(diffBlock.length));
    patch.write(offset(newSize));
    patch.write(controlBlock);
    patch.write(diffBlock);
    patch.write(extraBlock);
    return patch.toByteArray();
  }

  private static byte[] offset(long value) {
    byte[] result = new byte[8];
    long magnitude = Math.abs(value);
    for (int i = 0; i < 8; i++) result[i] = (byte) (magnitude >>> (8 * i));
    if (value < 0) result[7] |= (byte) 0x80;
    return result;
  }

  private static byte[] compress(byte[] data) throws IOException {
    var out = new ByteArrayOutputStream();
    try (OutputStream stream = new BZip2CompressorOutputStream(out)) { stream.write(data); }
    return out.toByteArray();
  }
}
