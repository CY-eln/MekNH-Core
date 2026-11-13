package c_y.meknh_core.common.gui.element;

import c_y.meknh_core.common.gui.layout.MachineGuiTextures;
import c_y.meknh_core.common.gui.layout.MekGuiTextures;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

public final class ProgressElement {
   public static final ResourceLocation DEFAULT_TEXTURE = MachineGuiTextures.PROGRESS_BAR;

   private ProgressElement() {
   }

   public static void render(GuiGraphics graphics, @Nullable ResourceLocation override, float ratio, int x, int y, int width, int height) {
      ResourceLocation texture = override != null ? override : DEFAULT_TEXTURE;
      if (height > 0) {
         graphics.blit(texture, x, y, width, height, 0.0F, 0.0F, width, height, width, height * 3);
         int filled = Math.round((float)width * Mth.clamp(ratio, 0.0F, 1.0F));
         if (filled > 0) {
            graphics.blit(texture, x, y, filled, height, 0.0F, (float)height, filled, height, width, height * 3);
         }
      }
   }

   /**
    * Mekanism 电力机器/熔炉的 {@code ProgressType.BAR}：25×9，左右各留 1px 内边距。
    */
   public static void renderMekBar(GuiGraphics graphics, float ratio, int x, int y) {
      renderMekBar(graphics, true, ratio, x, y);
   }

   public static void renderMekBar(GuiGraphics graphics, boolean active, float ratio, int x, int y) {
      final int width = 25;
      final int height = 9;
      final int textureHeight = height * 3;
      graphics.blit(MekGuiTextures.PROGRESS_BAR, x, y, 0, 0, width, height, width, textureHeight);
      if (!active) {
         return;
      }
      int filled = Math.round((width - 2) * Mth.clamp(ratio, 0.0F, 1.0F));
      if (filled > 0) {
         graphics.blit(MekGuiTextures.PROGRESS_BAR, x + 1, y, 1, height, filled, height, width, textureHeight);
      }
   }
}
