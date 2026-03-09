package com.skyhigh.checkin.exception;

public class WaitlistOfferExpiredException extends SkyHighBaseException {
    public WaitlistOfferExpiredException() {
        super("The waitlist seat offer has expired. You have been placed back in the queue.",
              "WAITLIST_OFFER_EXPIRED");
    }
}

