package com.hobeoung.edufactory.common;

/** pgvector 문자열 표현 변환 */
public final class Vectors {

  private Vectors() {}

  public static String toPg(float[] v) {
    StringBuilder sb = new StringBuilder(v.length * 10);
    sb.append('[');
    for (int i = 0; i < v.length; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(v[i]);
    }
    return sb.append(']').toString();
  }

  public static float[] normalize(float[] v) {
    double norm = 0;
    for (float x : v) {
      norm += x * x;
    }
    norm = Math.sqrt(norm);
    if (norm == 0) {
      return v;
    }
    float[] out = new float[v.length];
    for (int i = 0; i < v.length; i++) {
      out[i] = (float) (v[i] / norm);
    }
    return out;
  }
}
