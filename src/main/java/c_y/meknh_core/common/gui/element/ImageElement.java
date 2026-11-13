package c_y.meknh_core.common.gui.element;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public final class ImageElement {
   private ImageElement() {
   }

   public static void render(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height) {
      graphics.blit(texture, x, y, width, height, 0.0F, 0.0F, 16, 16, 16, 16);
   }
}
