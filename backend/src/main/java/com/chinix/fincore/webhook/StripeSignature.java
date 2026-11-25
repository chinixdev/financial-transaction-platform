package com.chinix.fincore.webhook;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Firma de webhooks al estilo Stripe: cabecera "t=<unix>,v1=<hmac>", donde
 * hmac = HMAC-SHA256(secreto, "<t>.<cuerpo>"). Se rechaza si la firma no coincide
 * o si el mensaje es demasiado antiguo (protege contra la repetición de mensajes).
 */
public final class StripeSignature {

  public static final long TOLERANCE_SECONDS = 300;

  private StripeSignature() {}

  public static String header(String payload, String secret, long timestamp) {
    return "t=" + timestamp + ",v1=" + hmac(timestamp + "." + payload, secret);
  }

  public static boolean valid(String payload, String header, String secret, long nowSeconds) {
    if (header == null) return false;
    String timestamp = null;
    String signature = null;
    for (String part : header.split(",")) {
      String[] kv = part.trim().split("=", 2);
      if (kv.length != 2) continue;
      if (kv[0].equals("t")) timestamp = kv[1];
      if (kv[0].equals("v1")) signature = kv[1];
    }
    if (timestamp == null || signature == null) return false;
    long t;
    try {
      t = Long.parseLong(timestamp);
    } catch (NumberFormatException e) {
      return false;
    }
    if (Math.abs(nowSeconds - t) > TOLERANCE_SECONDS) return false;
    // Comparación en tiempo constante (evita ataques por temporización).
    return MessageDigest.isEqual(
      hmac(t + "." + payload, secret).getBytes(StandardCharsets.UTF_8),
      signature.getBytes(StandardCharsets.UTF_8)
    );
  }

  private static String hmac(String data, String secret) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return java.util.HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
