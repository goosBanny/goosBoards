package me.goosbanny.goosboards.scene;

/**
 * An immutable 2D integer bounding box in canvas pixel space.
 *
 * @param x      the left X coordinate in pixels
 * @param y      the top Y coordinate in pixels
 * @param width  the width in pixels
 * @param height the height in pixels
 */
public record Rect(int x, int y, int width, int height) {

    public boolean contains(int px, int py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }
}
