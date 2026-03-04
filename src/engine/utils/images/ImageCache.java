package engine.utils.images;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * ImageCache
 *
 * Caches render-ready images (wich are BufferedImage) indexed by a composite
 * key containing angle, color, imageId and size. This avoids regenerating
 * images on every frame and ensures that the Renderer can blit pre-built
 * GPU-compatible images at maximum performance.
 *
 * Each unique visual configuration is created once (putInCache()) using the
 * current GraphicsConfiguration, producing a hardware-accelerated, compatible
 * BufferedImage. Subsequent requests for the same parameters return the same
 * cached image, minimizing CPU work and memory churn during rendering.
 *
 * In the current implementation, createSprite() provides a fallback procedural
 * sprite (a colored circle).
 */
public class ImageCache {

    private GraphicsConfiguration gc;
    private Images baseImages;
    private final Map<ImageCacheKeyDTO, BufferedImage> cache = new HashMap<>(2048);
    private volatile long hits = 0;
    private volatile long fails = 0;

    public ImageCache(GraphicsConfiguration gc, Images baseImages) {
        this.gc = gc;
        this.baseImages = baseImages;
    }

    /**
     * PUBLICS
     */
    public BufferedImage getImage(int angle, String assetId, int size) {
        ImageCacheKeyDTO key = new ImageCacheKeyDTO(angle, assetId, size);
        BufferedImage image = this.cache.get(key);

        if (image == null) {
            this.fails++;
            image = this.putInCache(angle, assetId, size);
            this.cache.put(key, image);
        } else {
            this.hits++;
        }

        return image;
    }

    public long getHits() {
        return this.hits;
    }

    public double getHitsPercentage() {
        if (this.hits == 0) {
            return 0d;
        }

        double hitsPctg = (double) this.hits / (double) (this.hits + this.fails);
        return hitsPctg * 100d;
    }

    public long getFails() {
        return this.fails;
    }

    public int size() {
        return this.cache.size();
    }

    public void setGraphicsConfiguration(GraphicsConfiguration gc) {
        this.gc = gc;
    }

    /**
     * PRIVATES
     */
    private BufferedImage putInCache(int angle, String assetId, int size) {
        if (this.gc == null) {
            throw new IllegalStateException("ImageCache: GraphicsConfiguration is null");
        }

        // El canvas debe ser suficientemente grande para contener la imagen rotada.
        // La diagonal de un cuadrado de lado `size` es size*√2 ≈ size*1.415.
        // Redondeamos al entero par más cercano para centrado pixel-perfect.
        int canvasSize = (int) Math.ceil(size * Math.sqrt(2));
        if ((canvasSize & 1) == 1) canvasSize++; // forzar par

        BufferedImage image = gc.createCompatibleImage(canvasSize, canvasSize, Transparency.TRANSLUCENT);
        Graphics2D g2 = image.createGraphics();

        ImageDTO imageDto = this.baseImages.getImage(assetId);

        try {
            if (imageDto != null) {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                double center = canvasSize * 0.5;
                // Rotar siempre alrededor del centro del canvas
                g2.rotate(Math.toRadians(angle), center, center);
                // Dibujar la imagen centrada en el canvas
                double offset = (canvasSize - size) * 0.5;
                g2.drawImage(imageDto.image, (int) offset, (int) offset, size, size, null);
            } else {
                // Fallback: círculo rojo centrado
                double offset = (canvasSize - size) * 0.5;
                g2.setColor(Color.RED);
                g2.fillOval((int) offset, (int) offset, size, size);
            }
        } finally {
            g2.dispose();
        }
        return image;
    }
}
