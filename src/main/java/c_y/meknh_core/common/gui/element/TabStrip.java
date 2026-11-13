package c_y.meknh_core.common.gui.element;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import c_y.meknh_core.common.gui.layout.MekGuiTextures;
import c_y.meknh_core.common.gui.render.GuiDraw;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Mek 机器侧边标签页：侧配置、传输配置、能量、安全、红石、问题提示。
 * 不包含自动平分（sorting）和升级（upgrade）。
 *
 * <p>尺寸与 Mek {@code GuiInsetElement} 一致：holder 26×26，内部按钮 18×18；
 * 能量/问题图标本身也是 26×26。图集全部直接引用 {@code mekanism:gui/...}。</p>
 */
public final class TabStrip {

   private static final int TAB_SIZE = 26;
   private static final int BUTTON_INSET = 4;
   private static final int ICON = 18;

   /**
    * 仓室 GUI 的「自动搬运」Tab：位置在窗口右上角，与 Mek 机器右上角的红石 Tab 对齐。
    */
   public static final int AUTO_IO_X = 176;
   public static final int AUTO_IO_Y = 6;
   /** Mek {@code SpecialColors.TAB_REDSTONE_CONTROL}。 */
   public static final int AUTO_IO_TINT = 13174559;

   private TabStrip() {
   }

   /**
    * 自动搬运 Tab：底板/按钮与红石控制 Tab 相同，图标用 {@code redstone_control_high/disabled}。
    */
   public static void renderAutoIo(GuiGraphics graphics, int left, int top, boolean enabled) {
      drawInset(
            graphics,
            MekGuiTextures.TAB_HOLDER_RIGHT,
            enabled ? MekGuiTextures.TAB_REDSTONE_HIGH : MekGuiTextures.TAB_REDSTONE_DISABLED,
            left + AUTO_IO_X,
            top + AUTO_IO_Y,
            AUTO_IO_TINT
      );
   }

   /**
    * 自动搬运 Tab 的命中判定（整块 26×26）。
    */
   public static boolean isOverAutoIo(double mouseX, double mouseY, int left, int top) {
      return mouseX >= left + AUTO_IO_X && mouseX < left + AUTO_IO_X + TAB_SIZE
            && mouseY >= top + AUTO_IO_Y && mouseY < top + AUTO_IO_Y + TAB_SIZE;
   }

   public static void render(GuiGraphics graphics, int left, int top, int redstoneOrdinal, boolean privateMode, int warningCode) {
      drawInset(graphics, MekGuiTextures.TAB_HOLDER_LEFT, MekGuiTextures.TAB_CONFIG, left - TAB_SIZE, top + 6, 0xFFFFFF);
      drawInset(graphics, MekGuiTextures.TAB_HOLDER_LEFT, MekGuiTextures.TAB_TRANSPORTER, left - TAB_SIZE, top + 34, 0xFFFFFF);
      if (warningCode != 0) {
         // 与 Mek GuiWarningTab(left=true) 一致：左侧、能量 tab 上方。
         graphics.blit(MekGuiTextures.TAB_WARNING_LEFT, left - TAB_SIZE, top + 109, 0.0F, 0.0F, TAB_SIZE, TAB_SIZE, TAB_SIZE, TAB_SIZE);
      }
      graphics.blit(MekGuiTextures.TAB_ENERGY, left - TAB_SIZE, top + 137, 0.0F, 0.0F, TAB_SIZE, TAB_SIZE, TAB_SIZE, TAB_SIZE);

      // 红石 tab 保持在用户指定位置（右侧最上，原升级 tab 位置）。
      drawInset(graphics, MekGuiTextures.TAB_HOLDER_RIGHT, redstoneIcon(redstoneOrdinal), left + 176, top + 6, 13174559);
      if ((warningCode & 8) != 0) {
         RenderSystem.enableBlend();
         RenderSystem.blendFunc(SourceFactor.DST_COLOR, DestFactor.ZERO);
         graphics.blit(MekGuiTextures.WARNING, left + 176, top + 6, 0, 0, TAB_SIZE, TAB_SIZE, 256, 256);
         RenderSystem.disableBlend();
      }
      drawInset(
            graphics,
            MekGuiTextures.TAB_HOLDER_RIGHT,
            privateMode ? MekGuiTextures.TAB_SECURITY_PRIVATE : MekGuiTextures.TAB_SECURITY_PUBLIC,
            left + 176,
            top + 34,
            16752992
      );
   }

   public static net.minecraft.network.chat.Component tooltip(Tab tab, int redstoneOrdinal, boolean privateMode) {
      return switch (tab) {
         case CONFIG -> net.minecraft.network.chat.Component.translatable("gui.meknh_core.tab.config");
         case TRANSPORTER -> net.minecraft.network.chat.Component.translatable("gui.meknh_core.tab.transporter");
         case ENERGY -> net.minecraft.network.chat.Component.translatable("gui.meknh_core.tab.energy");
         case SECURITY -> net.minecraft.network.chat.Component.translatable(privateMode
               ? "gui.meknh_core.tab.security.private" : "gui.meknh_core.tab.security.public");
         case REDSTONE -> c_y.meknh_core.common.machine.config.RedstoneMode
               .byOrdinal(redstoneOrdinal).displayName();
         case WARNING -> net.minecraft.network.chat.Component.translatable("gui.meknh_core.tab.warning");
      };
   }

   @Nullable
   public static Tab tabAt(int mouseX, int mouseY, int left, int top, boolean warning) {      if (inRect(mouseX, mouseY, left - TAB_SIZE, top + 6)) {
         return Tab.CONFIG;
      } else if (inRect(mouseX, mouseY, left - TAB_SIZE, top + 34)) {
         return Tab.TRANSPORTER;
      } else if (warning && inRect(mouseX, mouseY, left - TAB_SIZE, top + 109)) {
         return Tab.WARNING;
      } else if (inRect(mouseX, mouseY, left - TAB_SIZE, top + 137)) {
         return Tab.ENERGY;
      } else if (inRect(mouseX, mouseY, left + 176, top + 6)) {
         return Tab.REDSTONE;
      } else if (inRect(mouseX, mouseY, left + 176, top + 34)) {
         return Tab.SECURITY;
      } else {
         return null;
      }
   }

   private static ResourceLocation redstoneIcon(int ordinal) {
      return switch (ordinal) {
         case 1 -> MekGuiTextures.TAB_REDSTONE_HIGH;
         case 2 -> MekGuiTextures.TAB_REDSTONE_LOW;
         default -> MekGuiTextures.TAB_REDSTONE_DISABLED;
      };
   }

   private static boolean inRect(int mouseX, int mouseY, int x, int y) {
      return mouseX >= x && mouseX < x + TAB_SIZE && mouseY >= y && mouseY < y + TAB_SIZE;
   }

   private static void drawInset(GuiGraphics graphics, ResourceLocation holder, ResourceLocation icon, int x, int y, int tint) {
      float red = (float)(tint >> 16 & 0xFF) / 255.0F;
      float green = (float)(tint >> 8 & 0xFF) / 255.0F;
      float blue = (float)(tint & 0xFF) / 255.0F;
      graphics.setColor(red, green, blue, 1.0F);
      GuiDraw.nineSliceXY(graphics, holder, x, y, TAB_SIZE, TAB_SIZE, 4, 4, TAB_SIZE, 9, 0, 0, TAB_SIZE, 9);
      graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
      GuiDraw.nineSliceXY(
            graphics,
            MekGuiTextures.TAB_BUTTON,
            x + BUTTON_INSET,
            y + BUTTON_INSET,
            ICON,
            ICON,
            20,
            4,
            200,
            60,
            0,
            20,
            200,
            20
      );
      graphics.blit(icon, x + BUTTON_INSET, y + BUTTON_INSET, ICON, ICON, 0.0F, 0.0F, ICON, ICON, ICON, ICON);
   }

   public static enum Tab {
      CONFIG,
      TRANSPORTER,
      ENERGY,
      SECURITY,
      REDSTONE,
      WARNING;
   }
}
