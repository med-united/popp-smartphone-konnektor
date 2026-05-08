package de.servicehealth.popp.session;

public class EgkException extends RuntimeException {
  public static final EgkException CARD_SESSION_MODIFIED =
      new EgkException("CardSession id has already been set. Can not be modified");

  private EgkException(String message) {
    super(message);
  }
}
