package com.skyhigh.checkin.exception;

import lombok.Getter;

import java.util.UUID;

@Getter
public class ResourceNotFoundException extends SkyHighBaseException {

    private final String resourceType;
    private final String resourceId;

    public ResourceNotFoundException(String resourceType, UUID resourceId) {
        super(resourceType + " not found with ID: " + resourceId, resourceType.toUpperCase() + "_NOT_FOUND", false);
        this.resourceType = resourceType;
        this.resourceId = resourceId.toString();
    }

    public ResourceNotFoundException(String resourceType, String resourceId) {
        super(resourceType + " not found: " + resourceId, resourceType.toUpperCase() + "_NOT_FOUND", false);
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }
}

