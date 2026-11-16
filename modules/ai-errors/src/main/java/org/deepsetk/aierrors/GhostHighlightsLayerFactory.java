package org.deepsetk.aierrors;

import org.netbeans.spi.editor.highlighting.HighlightsLayer;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory;
import org.netbeans.spi.editor.highlighting.ZOrder;

/** Capa que dibuja el texto fantasma encima del editor Java. */
public final class GhostHighlightsLayerFactory implements HighlightsLayerFactory {

    @Override
    public HighlightsLayer[] createLayers(Context context) {
        java.util.logging.Logger.getLogger(GhostHighlightsLayerFactory.class.getName())
                .fine(() -> "Ghost layer creada para " + context.getDocument().getProperty("mimeType"));
        return new HighlightsLayer[]{
            HighlightsLayer.create(
                    GhostCompletion.class.getName(),
                    ZOrder.SYNTAX_RACK.forPosition(1600),
                    false,
                    GhostCompletion.bagFor(context.getDocument()))
        };
    }
}

