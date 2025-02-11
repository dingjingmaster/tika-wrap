package com.github.dingjingmaster.tika.main.ndimporter.fingerprint.exception;

public class NoFeatureInformationException extends Exception {
   private static final long serialVersionUID = 7543182553250995765L;

   public NoFeatureInformationException() {
   }

   public NoFeatureInformationException(String message) {
      super(message);
   }

   public NoFeatureInformationException(String message, Throwable cause) {
      super(message, cause);
   }

   public NoFeatureInformationException(Throwable cause) {
      super(cause);
   }

   protected NoFeatureInformationException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
      super(message, cause, enableSuppression, writableStackTrace);
   }
}
