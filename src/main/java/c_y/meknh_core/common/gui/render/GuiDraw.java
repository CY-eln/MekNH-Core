package c_y.meknh_core.common.gui.render;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public final class GuiDraw {
   private GuiDraw() {
   }

   public static void nineSliceXY(
      GuiGraphics graphics,
      ResourceLocation texture,
      int x,
      int y,
      int width,
      int height,
      int borderX,
      int borderY,
      int textureWidth,
      int textureHeight,
      int uOffset,
      int vOffset,
      int regionWidth,
      int regionHeight
   ) {
      int bx = Math.min(borderX, width / 2);
      int by = Math.min(borderY, height / 2);
      if (bx > 0 && by > 0) {
         int middleWidth = width - bx * 2;
         int middleHeight = height - by * 2;
         int regionMiddleWidth = regionWidth - bx * 2;
         int regionMiddleHeight = regionHeight - by * 2;
         graphics.blit(texture, x, y, bx, by, (float)uOffset, (float)vOffset, bx, by, textureWidth, textureHeight);
         graphics.blit(texture, x + width - bx, y, bx, by, (float)(uOffset + regionWidth - bx), (float)vOffset, bx, by, textureWidth, textureHeight);
         graphics.blit(texture, x, y + height - by, bx, by, (float)uOffset, (float)(vOffset + regionHeight - by), bx, by, textureWidth, textureHeight);
         graphics.blit(
            texture,
            x + width - bx,
            y + height - by,
            bx,
            by,
            (float)(uOffset + regionWidth - bx),
            (float)(vOffset + regionHeight - by),
            bx,
            by,
            textureWidth,
            textureHeight
         );
         if (middleWidth > 0) {
            graphics.blit(texture, x + bx, y, middleWidth, by, (float)(uOffset + bx), (float)vOffset, regionMiddleWidth, by, textureWidth, textureHeight);
            graphics.blit(
               texture,
               x + bx,
               y + height - by,
               middleWidth,
               by,
               (float)(uOffset + bx),
               (float)(vOffset + regionHeight - by),
               regionMiddleWidth,
               by,
               textureWidth,
               textureHeight
            );
         }

         if (middleHeight > 0) {
            graphics.blit(texture, x, y + by, bx, middleHeight, (float)uOffset, (float)(vOffset + by), bx, regionMiddleHeight, textureWidth, textureHeight);
            graphics.blit(
               texture,
               x + width - bx,
               y + by,
               bx,
               middleHeight,
               (float)(uOffset + regionWidth - bx),
               (float)(vOffset + by),
               bx,
               regionMiddleHeight,
               textureWidth,
               textureHeight
            );
         }

         if (middleWidth > 0 && middleHeight > 0) {
            graphics.blit(
               texture,
               x + bx,
               y + by,
               middleWidth,
               middleHeight,
               (float)(uOffset + bx),
               (float)(vOffset + by),
               regionMiddleWidth,
               regionMiddleHeight,
               textureWidth,
               textureHeight
            );
         }
      } else {
         graphics.blit(texture, x, y, width, height, (float)uOffset, (float)vOffset, regionWidth, regionHeight, textureWidth, textureHeight);
      }
   }

   public static void nineSlice(
      GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height, int border, int textureWidth, int textureHeight
   ) {
      int slice = Math.min(border, Math.min(width, height) / 2);
      if (slice <= 0) {
         graphics.blit(texture, x, y, width, height, 0.0F, 0.0F, textureWidth, textureHeight, textureWidth, textureHeight);
      } else {
         int middleWidth = width - slice * 2;
         int middleHeight = height - slice * 2;
         int textureMiddleWidth = textureWidth - slice * 2;
         int textureMiddleHeight = textureHeight - slice * 2;
         graphics.blit(texture, x, y, slice, slice, 0.0F, 0.0F, slice, slice, textureWidth, textureHeight);
         graphics.blit(texture, x + width - slice, y, slice, slice, (float)(textureWidth - slice), 0.0F, slice, slice, textureWidth, textureHeight);
         graphics.blit(texture, x, y + height - slice, slice, slice, 0.0F, (float)(textureHeight - slice), slice, slice, textureWidth, textureHeight);
         graphics.blit(
            texture,
            x + width - slice,
            y + height - slice,
            slice,
            slice,
            (float)(textureWidth - slice),
            (float)(textureHeight - slice),
            slice,
            slice,
            textureWidth,
            textureHeight
         );
         if (middleWidth > 0) {
            graphics.blit(texture, x + slice, y, middleWidth, slice, (float)slice, 0.0F, textureMiddleWidth, slice, textureWidth, textureHeight);
            graphics.blit(
               texture,
               x + slice,
               y + height - slice,
               middleWidth,
               slice,
               (float)slice,
               (float)(textureHeight - slice),
               textureMiddleWidth,
               slice,
               textureWidth,
               textureHeight
            );
         }

         if (middleHeight > 0) {
            graphics.blit(texture, x, y + slice, slice, middleHeight, 0.0F, (float)slice, slice, textureMiddleHeight, textureWidth, textureHeight);
            graphics.blit(
               texture,
               x + width - slice,
               y + slice,
               slice,
               middleHeight,
               (float)(textureWidth - slice),
               (float)slice,
               slice,
               textureMiddleHeight,
               textureWidth,
               textureHeight
            );
         }

         if (middleWidth > 0 && middleHeight > 0) {
            graphics.blit(
               texture,
               x + slice,
               y + slice,
               middleWidth,
               middleHeight,
               (float)slice,
               (float)slice,
               textureMiddleWidth,
               textureMiddleHeight,
               textureWidth,
               textureHeight
            );
         }
      }
   }
}
