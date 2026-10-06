package dev.hbrauveres.xove.room;

/** How full the room is (spec 0104): its seats, how many are taken (kept ones too), and how many wait. */
public record Occupancy(int total, int taken, int waiting) {
}
