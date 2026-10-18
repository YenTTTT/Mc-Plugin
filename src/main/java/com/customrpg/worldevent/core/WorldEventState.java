package com.customrpg.worldevent.core;

public enum WorldEventState {
    SCHEDULED, ANNOUNCED, FALLING, IMPACTING, ACTIVE, COMPLETED, FAILED, EXPIRED, CLEANING, CLOSED;

    public boolean canTransitionTo(WorldEventState next) {
        if (this == CLOSED || this == next) return false;
        if (next == FAILED) return ordinal() < COMPLETED.ordinal();
        if (next == EXPIRED) return ordinal() < COMPLETED.ordinal();
        return switch (this) {
            case SCHEDULED -> next == ANNOUNCED;
            case ANNOUNCED -> next == FALLING;
            case FALLING -> next == IMPACTING;
            case IMPACTING -> next == ACTIVE;
            case ACTIVE -> next == COMPLETED;
            case COMPLETED, FAILED, EXPIRED -> next == CLEANING;
            case CLEANING -> next == CLOSED;
            default -> false;
        };
    }
}
