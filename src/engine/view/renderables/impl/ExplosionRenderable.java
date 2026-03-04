package engine.view.renderables.impl;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import engine.utils.images.ImageDTO;
import engine.utils.images.Images;
import engine.view.renderables.ports.RenderDTO;

public class ExplosionRenderable extends Renderable {

    public  static final String ASSET_ID     = "explosion_sheet";
    public  static final int    DEFAULT_SIZE = 160;

    private static final int    COLS         = 4;
    private static final int    ROWS         = 4;
    private static final int    TOTAL_FRAMES = 16;
    private static final double DURATION_MS  = 900.0;

    private final long            birthMs;
    private final int             drawSize;
    private final Images          imagesRef;   // guardamos la referencia para lazy init
    private       BufferedImage[] frames = null;

    public ExplosionRenderable(RenderDTO renderData, Images images, long currentFrame) {
        super(renderData.entityId, ASSET_ID, null, currentFrame);
        this.birthMs   = System.currentTimeMillis();
        this.drawSize  = (int) Math.max(renderData.size > 0 ? renderData.size : DEFAULT_SIZE, 64);
        this.imagesRef = images;
        super.setRenderDataDirect(renderData, currentFrame);
        // Intentar construir frames ya — si images está listo
        if (images != null) buildFrames(images);
    }

    public boolean isFinished() {
        return System.currentTimeMillis() - birthMs >= (long) DURATION_MS;
    }

    /** Devuelve el frame actual para que isVisible calcule el bounding box correcto. */
    @Override
    public java.awt.image.BufferedImage getImage() {
        if (frames == null || frames.length == 0) return null;
        long elapsed  = System.currentTimeMillis() - birthMs;
        int  frameIdx = (int) Math.min((elapsed / DURATION_MS) * TOTAL_FRAMES, TOTAL_FRAMES - 1);
        return frames[frameIdx];
    }

    /**
     * Sobreescribe update para evitar que el padre llame a ImageCache con size=0.
     * ExplosionRenderable gestiona sus propias imágenes (frames del spritesheet).
     */
    @Override
    public void update(RenderDTO renderInfo, long currentFrame) {
        // Solo actualizar renderData — no tocar ImageCache
        super.setRenderDataDirect(renderInfo, currentFrame);
    }

    @Override
    public void paint(Graphics2D g, long currentFrame) {
        // Lazy init: construir frames si aún no están listos
        if (frames == null && imagesRef != null) buildFrames(imagesRef);
        if (frames == null || frames.length == 0) return;

        RenderDTO data = this.getRenderData();
        if (data == null) return;

        long elapsed  = System.currentTimeMillis() - birthMs;
        int  frameIdx = (int) Math.min((elapsed / DURATION_MS) * TOTAL_FRAMES, TOTAL_FRAMES - 1);

        BufferedImage frame = frames[frameIdx];
        if (frame == null) return;

        int hw = frame.getWidth()  / 2;
        int hh = frame.getHeight() / 2;
        g.drawImage(frame, (int) data.posX - hw, (int) data.posY - hh, null);
    }

    private void buildFrames(Images images) {
        ImageDTO dto = images.getImage(ASSET_ID);
        if (dto == null || dto.image == null) return;

        BufferedImage sheet = dto.image;
        int fw = sheet.getWidth()  / COLS;
        int fh = sheet.getHeight() / ROWS;

        frames = new BufferedImage[TOTAL_FRAMES];
        for (int i = 0; i < TOTAL_FRAMES; i++) {
            int col = i % COLS;
            int row = i / COLS;
            BufferedImage raw    = sheet.getSubimage(col * fw, row * fh, fw, fh);
            BufferedImage scaled = new BufferedImage(drawSize, drawSize, BufferedImage.TYPE_INT_ARGB);
            Graphics2D sg = scaled.createGraphics();
            sg.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            sg.drawImage(raw, 0, 0, drawSize, drawSize, null);
            sg.dispose();
            frames[i] = scaled;
        }
    }
}
