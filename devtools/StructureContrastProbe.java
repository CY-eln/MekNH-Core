import c_y.meknh_core.client.structure.StructurePreviewPalette;

/** Pure production-palette check, not a Minecraft screenshot/UI test. */
public class StructureContrastProbe {
    static double channel(int c) { double s = c / 255.0; return s <= .04045 ? s / 12.92 : Math.pow((s + .055) / 1.055, 2.4); }
    static double luminance(int rgb) { return .2126 * channel((rgb >>> 16) & 255) + .7152 * channel((rgb >>> 8) & 255) + .0722 * channel(rgb & 255); }
    static void check(String state, int foreground, int background) {
        double a = luminance(foreground), b = luminance(background);
        double contrast = (Math.max(a,b) + .05) / (Math.min(a,b) + .05);
        System.out.printf("%s contrast=%.2f:1%n", state, contrast);
        if (contrast < 4.5) throw new AssertionError(state + " text contrast below 4.5:1");
    }
    public static void main(String[] args) {
        check("body", StructurePreviewPalette.BODY_TEXT, StructurePreviewPalette.PANEL);
        check("button", StructurePreviewPalette.CONTROL_TEXT, StructurePreviewPalette.BUTTON);
        check("hover", StructurePreviewPalette.CONTROL_TEXT, StructurePreviewPalette.BUTTON_HOVER);
        check("pressed", StructurePreviewPalette.CONTROL_TEXT, StructurePreviewPalette.BUTTON_PRESSED);
        check("material/candidate row", StructurePreviewPalette.CONTROL_TEXT, StructurePreviewPalette.LIST_ROW);
        check("material card border", StructurePreviewPalette.LIST_BORDER, StructurePreviewPalette.LIST_ROW);
        System.out.println("PASS production palette: all text pairs and card border >= 4.5:1");
    }
}
