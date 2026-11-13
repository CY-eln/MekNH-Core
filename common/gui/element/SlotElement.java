package c_y.meknh_core.common.gui.element;

import c_y.meknh_core.common.gui.layout.MachineGuiTextures;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public final class SlotElement {
   private SlotElement() {
   }

   public static void render(GuiGraphics graphics, @Nullable ResourceLocation override, SlotElement.Type type, int x, int y, int width, int height) {
      ResourceLocation texture = override != null ? override : type.texture();
      graphics.blit(texture, x, y, width, height, 0.0F, 0.0F, 16, 16, 16, 16);
   }

   public static enum Type {
      NORMAL(MachineGuiTextures.SLOT),
      INPUT(MachineGuiTextures.SLOT_INPUT),
      OUTPUT(MachineGuiTextures.SLOT_OUTPUT),
      POWER(MachineGuiTextures.SLOT_POWER);

      private final ResourceLocation texture;

      private Type(ResourceLocation texture) {
         this.texture = texture;
      }

      public ResourceLocation texture() {
         return this.texture;
      }
   }
}
