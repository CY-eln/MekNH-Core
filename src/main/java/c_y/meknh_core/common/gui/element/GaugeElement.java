package c_y.meknh_core.common.gui.element;

import c_y.meknh_core.common.gui.layout.MachineGuiTextures;
import c_y.meknh_core.common.gui.render.GuiDraw;
import mekanism.api.chemical.ChemicalStack;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.GuiUtils.TilingDirection;
import mekanism.client.render.MekanismRenderer;
import mekanism.client.render.MekanismRenderer.FluidTextureType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

public final class GaugeElement {
   public static final ResourceLocation GAUGE_FRAME = MachineGuiTextures.GAUGE;
   public static final ResourceLocation FILL_HEAT = MachineGuiTextures.GAUGE_INPUT;
   public static final ResourceLocation FILL_STEAM = MachineGuiTextures.GAUGE_STEAM;
   public static final ResourceLocation FILL_FLUID_IN = MachineGuiTextures.GAUGE_INPUT;
   public static final ResourceLocation FILL_FLUID_OUT = MachineGuiTextures.GAUGE_OUTPUT;
   public static final ResourceLocation FILL_GAS_IN = MachineGuiTextures.GAUGE_GAS_INPUT;
   public static final ResourceLocation FILL_GAS_OUT = MachineGuiTextures.GAUGE_GAS_OUTPUT;
   public static final ResourceLocation POWER_FRAME = MachineGuiTextures.POWER_BAR;
   public static final ResourceLocation POWER_FILL = MachineGuiTextures.POWER_FILL;

   private GaugeElement() {
   }

   /**
    * 实际罐内容：有流体/化学品时用它绘制真实贴图与 tint，而不是固定 FILL_* 图。
    */
   public record Content(@Nullable FluidStack fluid, @Nullable ChemicalStack chemical) {
      public static Content of(FluidStack fluid) {
         return fluid == null || fluid.isEmpty() ? null : new Content(fluid, null);
      }

      public static Content of(ChemicalStack chemical) {
         return chemical == null || chemical.isEmpty() ? null : new Content(null, chemical);
      }
   }

   public static void renderPower(GuiGraphics graphics, @Nullable ResourceLocation fillOverride, float ratio, int x, int y, int width, int height) {
      GuiDraw.nineSlice(graphics, POWER_FRAME, x, y, width, height, 2, 5, 5);
      ResourceLocation fill = fillOverride != null ? fillOverride : POWER_FILL;
      int innerWidth = Math.max(1, width - 2);
      int innerHeight = Math.max(0, height - 2);
      int display = Math.round(Mth.clamp(ratio, 0.0F, 1.0F) * 52.0F);
      int scaled = Math.round(display * (innerHeight / 52.0F));
      if (scaled > 0) {
         // GuiVerticalPowerBar#renderBarOverlay: 4x52 原图，从底部向上绘制。
         graphics.blit(fill, x + 1, y + 1 + innerHeight - scaled, innerWidth, scaled,
               0.0F, 0.0F, 4, display, 4, 52);
      }
   }

   public static void renderGauge(
      GuiGraphics graphics, @Nullable ResourceLocation fillOverride, ResourceLocation defaultFill, float ratio, int x, int y, int width, int height
   ) {
      renderGauge(graphics, fillOverride, defaultFill, ratio, x, y, width, height, null);
   }

   public static void renderGauge(
      GuiGraphics graphics, @Nullable ResourceLocation fillOverride, ResourceLocation defaultFill,
      float ratio, int x, int y, int width, int height, @Nullable Content content
   ) {
      int filled = Math.round((float)height * Mth.clamp(ratio, 0.0F, 1.0F));
      if (filled > 0 && content != null) {
         TextureAtlasSprite sprite = content.chemical() != null
               ? MekanismRenderer.getChemicalTexture(content.chemical())
               : MekanismRenderer.getFluidTexture(content.fluid(), FluidTextureType.STILL);
         if (sprite != null) {
            graphics.enableScissor(x, y + height - filled, x + width, y + height);
            if (content.chemical() != null) {
               int tint = content.chemical().getChemicalTint();
               graphics.setColor(((tint >> 16) & 0xFF) / 255.0F, ((tint >> 8) & 0xFF) / 255.0F,
                     (tint & 0xFF) / 255.0F, 1.0F);
            } else {
               MekanismRenderer.color(graphics, content.fluid());
            }
            GuiUtils.drawTiledSprite(graphics, x, y + height, 0, width, filled,
                  sprite, 16, 16, 0, TilingDirection.DOWN_RIGHT);
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.disableScissor();
         }
      } else if (filled > 0) {
         ResourceLocation fill = fillOverride != null ? fillOverride : defaultFill;
         graphics.blit(fill, x, y + height - filled, width, filled, 0.0F, 0.0F, 16, 16, 16, 16);
      }

      graphics.blit(GAUGE_FRAME, x, y, width, height, 0.0F, 0.0F, 16, 16, 16, 16);
   }

   public static boolean isFillTexture(ResourceLocation texture) {
      return texture.getPath().contains("gauge");
   }
}
