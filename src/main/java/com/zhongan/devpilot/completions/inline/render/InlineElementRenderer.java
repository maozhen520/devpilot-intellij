package com.zhongan.devpilot.completions.inline.render;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.TestOnly;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;

class InlineElementRenderer implements EditorCustomElementRenderer {
    private final Editor editor;
    private final String suffix;
    private boolean deprecated;
    private Color color;

    public InlineElementRenderer(Editor editor, String suffix, boolean deprecated) {
        this.editor = editor;
        this.suffix = suffix;
        this.deprecated = deprecated;
    }

    @Override
    public int calcWidthInPixels(Inlay inlay) {
        return editor.getContentComponent()
                .getFontMetrics(GraphicsUtils.INSTANCE.getFont(editor, deprecated)).stringWidth(suffix);
    }

    @TestOnly
    public String getContent() {
        return suffix;
    }

    @Override
    public void paint(Inlay inlay, Graphics g, Rectangle targetRegion, TextAttributes textAttributes) {
        color = color != null ? color : GraphicsUtils.INSTANCE.getColor();
        g.setColor(color);
        g.setFont(GraphicsUtils.INSTANCE.getFont(editor, deprecated));
        g.drawString(suffix, targetRegion.x, targetRegion.y + editor.getAscent());
    }
}
