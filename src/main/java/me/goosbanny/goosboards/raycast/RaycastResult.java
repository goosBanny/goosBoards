package me.goosbanny.goosboards.raycast;

import java.util.UUID;

public sealed interface RaycastResult permits RaycastResult.Hit, RaycastResult.Miss {
    record Hit(UUID displayId, int pixelX, int pixelY, double t) implements RaycastResult {}
    record Miss() implements RaycastResult {}

    static RaycastResult miss() {
        return new Miss();
    }
}
