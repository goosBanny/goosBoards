package me.goosbanny.goosboards.raycast;

public record Vector3d(double x, double y, double z) {
    public Vector3d add(Vector3d other) {
        return new Vector3d(x + other.x, y + other.y, z + other.z);
    }

    public Vector3d add(double dx, double dy, double dz) {
        return new Vector3d(x + dx, y + dy, z + dz);
    }

    public Vector3d subtract(Vector3d other) {
        return new Vector3d(x - other.x, y - other.y, z - other.z);
    }

    public Vector3d multiply(double scalar) {
        return new Vector3d(x * scalar, y * scalar, z * scalar);
    }

    public double dot(Vector3d other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public Vector3d cross(Vector3d other) {
        return new Vector3d(
                y * other.z - z * other.y,
                z * other.x - x * other.z,
                x * other.y - y * other.x
        );
    }

    public double lengthSquared() {
        return x * x + y * y + z * z;
    }

    public double length() {
        return Math.sqrt(lengthSquared());
    }

    public Vector3d normalize() {
        double len = length();
        if (len < 1e-12) {
            return new Vector3d(0.0, 0.0, 0.0);
        }
        return new Vector3d(x / len, y / len, z / len);
    }

    public double distanceSquared(double ox, double oy, double oz) {
        double dx = x - ox;
        double dy = y - oy;
        double dz = z - oz;
        return dx * dx + dy * dy + dz * dz;
    }

    public double distance(double ox, double oy, double oz) {
        return Math.sqrt(distanceSquared(ox, oy, oz));
    }

    public static double distanceSquared(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        double dz = z1 - z2;
        return dx * dx + dy * dy + dz * dz;
    }

    public static double distance(double x1, double y1, double z1, double x2, double y2, double z2) {
        return Math.sqrt(distanceSquared(x1, y1, z1, x2, y2, z2));
    }

    public double distanceSquared(Vector3d other) {
        if (other == null) return Double.MAX_VALUE;
        return distanceSquared(other.x, other.y, other.z);
    }

    public double distance(Vector3d other) {
        if (other == null) return Double.MAX_VALUE;
        return distance(other.x, other.y, other.z);
    }
}
