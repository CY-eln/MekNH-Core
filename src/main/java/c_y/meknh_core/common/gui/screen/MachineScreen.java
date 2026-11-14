package c_y.meknh_core.common.gui.screen;

import c_y.meknh_core.api.machine.MachineBlock;
import c_y.meknh_core.api.machine.MachineBlockEntity;
import c_y.meknh_core.client.MeknhUiTool;
import c_y.meknh_core.client.editor.MachineEditorOverlay;
import c_y.meknh_core.client.editor.MachineUiLayouts;
import c_y.meknh_core.client.editor.TexturePickerScreen;
import c_y.meknh_core.client.editor.UiElement;
import c_y.meknh_core.client.editor.UiElementKind;
import c_y.meknh_core.client.editor.UiElementRenderer;
import c_y.meknh_core.common.data.voltage.MachineLevel;
import c_y.meknh_core.common.gui.element.ChemicalBarElement;
import c_y.meknh_core.common.gui.element.GaugeElement;
import c_y.meknh_core.common.gui.element.ProgressElement;
import c_y.meknh_core.common.gui.element.ScreenTabs;
import c_y.meknh_core.common.gui.element.SideConfigPanel;
import c_y.meknh_core.common.gui.element.TabStrip;
import c_y.meknh_core.common.gui.element.TransporterPanel;
import c_y.meknh_core.common.gui.layout.MachineGuiTextures;
import c_y.meknh_core.common.gui.layout.MekGuiTextures;
import c_y.meknh_core.common.gui.layout.SteamGuiLayout;
import c_y.meknh_core.common.gui.machine.AlloySmelterScreen;
import c_y.meknh_core.common.gui.machine.MaceratorScreen;
import c_y.meknh_core.common.gui.menu.MachineMenu;
import c_y.meknh_core.common.gui.render.GuiDraw;
import c_y.meknh_core.common.machine.config.RedstoneMode;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.GuiUtils.TilingDirection;
import mekanism.client.render.MekanismRenderer;
import mekanism.client.render.MekanismRenderer.FluidTextureType;
import mekanism.common.registries.MekanismChemicals;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

public class MachineScreen extends AbstractContainerScreen<MachineMenu> {
   private static final int TEXT_COLOR = 4210752;
   @Nullable
   private MachineEditorOverlay editor;
   private final ScreenTabs tabs = new ScreenTabs();
   private final TransporterPanel transporter = new TransporterPanel();
   /** 0 = mB, 1 = B, 2 = %. */
   private int steamEnergyUnit;
   /** 电力机器能量显示单位：0=FE，1=J。 */
   private int energyUnit;

   public MachineScreen(MachineMenu menu, Inventory playerInventory, Component title) {
      super(menu, playerInventory, title);
      this.imageWidth = 176;
      // 合金炉电力/蒸汽版本共用蒸汽布局；其他机器保持原布局。
      boolean steamLayout = menu.usesSteamLayout();
      this.imageHeight = steamLayout ? 177 : 166;
      this.inventoryLabelY = steamLayout ? 85 : 73;
   }

   /**
    * 按机器类型选择界面类。合金炉/研磨机有独立类，其余机器使用通用界面。
    */
   public static MachineScreen create(MachineMenu menu, Inventory inventory, Component title) {
      ResourceLocation id = menu.definition() == null ? null : menu.definition().id();
      if (id != null) {
         if (id.equals(c_y.meknh_core.MekNHCore.rl("alloy_smelter"))) {
            return new AlloySmelterScreen(menu, inventory, title);
         }
         if (id.equals(c_y.meknh_core.MekNHCore.rl("macerator"))) {
            return new MaceratorScreen(menu, inventory, title);
         }
      }
      return new MachineScreen(menu, inventory, title);
   }

   protected void init() {
      super.init();
      this.tabs.init(this.machinePos());
      this.tabs.setEnergyClick(this::cycleEnergyUnit);
      this.tabs.setTransporterClick(this.transporter::toggle);      if (MeknhUiTool.isEditorMode() && ((MachineMenu)this.menu).definition() != null) {
         if (this.editor == null) {
            this.editor = new MachineEditorOverlay(this, ((MachineMenu)this.menu).definition(), ((MachineMenu)this.menu).level());
         }

         this.editor.init();
      } else {
         this.editor = null;
      }
   }

   protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
      GuiDraw.nineSlice(graphics, MachineGuiTextures.BASE, this.leftPos, this.topPos, this.imageWidth, this.imageHeight, 4, 256, 256);
      if (((MachineMenu)this.menu).usesSteamLayout()) {
         // 蒸汽布局：输入槽 - 进度条 - 输出槽；底部横向槽按机器类型绘制。
         int inputSlots = ((MachineMenu)this.menu).definition() == null ? 0 : ((MachineMenu)this.menu).definition().itemInputSlots();
         int outputSlots = ((MachineMenu)this.menu).definition() == null ? 0 : ((MachineMenu)this.menu).definition().itemOutputSlots();
         int machineSlots = ((MachineMenu)this.menu).machineSlotCount();
         for (int index = 0; index < ((MachineMenu)this.menu).slots.size(); index++) {
            Slot slot = (Slot)((MachineMenu)this.menu).slots.get(index);
            ResourceLocation texture;
            if (index < inputSlots) {
               texture = MekGuiTextures.SLOT_INPUT;
            } else if (index < machineSlots) {
               texture = MekGuiTextures.SLOT_OUTPUT;
            } else {
               texture = MekGuiTextures.SLOT_NORMAL;
            }
            graphics.blit(texture, this.leftPos + slot.x - 1, this.topPos + slot.y - 1, 0.0F, 0.0F, 18, 18, 18, 18);
         }
         // 横向结构：输入槽 - 进度条 - 输出槽。
         ProgressElement.renderMekBar(graphics, ((MachineMenu)this.menu).data(MachineMenu.DATA_RUNNING) != 0,
               this.progressRatio(),
               this.leftPos + SteamGuiLayout.progressX(inputSlots, outputSlots), this.topPos + SteamGuiLayout.PROGRESS_Y);
         if (((MachineMenu)this.menu).isSteam()) {
            MachineLevel level = ((MachineMenu)this.menu).level();
            long capacity = level == null ? 0L : level.steamCapacity();
            ChemicalBarElement.render(graphics, this.steamStack(), capacity, this.leftPos, this.topPos);
            ChemicalBarElement.renderDumpButton(graphics, this.leftPos, this.topPos);
         } else {
            // 电力合金炉/电炉等：右侧竖向能量条。
            GaugeElement.renderPower(graphics, null, this.energyRatio(), this.leftPos + 164, this.topPos + 16, 6, 54);
         }
         // 提取机 MEK 输出：18x18 输出槽竖向拉伸成罐体，再叠一张刻度。
         if (this.hasMekOutputSlot()) {
            int x = this.mekSlotX();
            int y = this.topPos + 16;
            // 输出槽宽度保持 18，仅纵向九宫格拉伸成罐体。
            GuiDraw.nineSliceXY(graphics, MekGuiTextures.SLOT_OUTPUT, x, y,
                  18, 58, 1, 1, 18, 18, 0, 0, 18, 18);
            // 一个 MEK 槽同时容纳流体或化学品，按当前内容绘制液面。
            MachineBlockEntity machine = ((MachineMenu)this.menu).machine();
            if (machine != null) {
               ChemicalStack gas = machine.outputGasTank() == null ? ChemicalStack.EMPTY : machine.outputGasTank().getStack();
               net.neoforged.neoforge.fluids.FluidStack fluid = machine.outputFluidTank() == null
                     ? net.neoforged.neoforge.fluids.FluidStack.EMPTY : machine.outputFluidTank().getFluid();
               int capacity = machine.outputGasTank() != null ? (int)machine.outputGasTank().getCapacity()
                     : machine.outputFluidTank() == null ? 0 : machine.outputFluidTank().getCapacity();
               int amount = !gas.isEmpty() ? (int)gas.getAmount() : fluid.getAmount();
               if (amount > 0 && capacity > 0) {
                  int innerHeight = 56;
                  int fill = Math.max(1, Math.round(innerHeight * Math.min(1.0F, amount / (float)capacity)));
                  TextureAtlasSprite sprite = null;
                  boolean colored = false;
                  if (!gas.isEmpty()) {
                     sprite = MekanismRenderer.getChemicalTexture(gas);
                     int tint = gas.getChemicalTint();
                     graphics.setColor(((tint >> 16) & 0xFF) / 255.0F,
                           ((tint >> 8) & 0xFF) / 255.0F, (tint & 0xFF) / 255.0F, 1.0F);
                     colored = true;
                  } else if (!fluid.isEmpty()) {
                     sprite = MekanismRenderer.getFluidTexture(fluid, FluidTextureType.STILL);
                     MekanismRenderer.color(graphics, fluid);
                     colored = true;
                  }
                  if (sprite != null) {
                     // drawTiledSprite 的 yPosition 是“底部”，从罐底向上铺。
                     GuiUtils.drawTiledSprite(graphics, x + 1, y + 1 + innerHeight,
                           0, 16, fill, sprite, 16, 16, 0, TilingDirection.DOWN_RIGHT);
                     if (colored) {
                        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
                     }
                  }
               }
            }
            graphics.blit(MachineGuiTextures.GAUGE, x + 1, y,
                  16, 58, 0.0F, 0.0F, 16, 58, 16, 58);
         }
         return;
      }

      this.renderLayout(graphics);

      for (Slot slot : ((MachineMenu)this.menu).slots) {
         if (this.isPlayerSlot(slot)) {
            graphics.blit(MachineGuiTextures.SLOT, this.leftPos + slot.x - 1, this.topPos + slot.y - 1, 0.0F, 0.0F, 18, 18, 18, 18);
         }
      }
   }

   private void renderLayout(GuiGraphics graphics) {
      if (((MachineMenu)this.menu).definition() != null) {
         List<UiElement> elements = MachineUiLayouts.current(((MachineMenu)this.menu).definition().id());
         if (elements == null || elements.isEmpty()) {
            elements = MachineUiLayouts.defaults(((MachineMenu)this.menu).definition(), ((MachineMenu)this.menu).level());
         }

         float storage = this.storageRatio();
         MachineBlockEntity machine = ((MachineMenu)this.menu).machine();

         for (UiElement element : elements) {
            GaugeElement.Content content = null;
            if (machine != null) {
               content = switch (element.kind()) {
                  case STEAM -> machine.steamTank() == null ? null : GaugeElement.Content.of(machine.steamTank().getStack());
                  case FLUID_IN -> machine.inputFluidTank() == null ? null : GaugeElement.Content.of(machine.inputFluidTank().getFluid());
                  case FLUID_OUT -> machine.outputFluidTank() == null ? null : GaugeElement.Content.of(machine.outputFluidTank().getFluid());
                  case GAS_IN -> machine.inputGasTank() == null ? null : GaugeElement.Content.of(machine.inputGasTank().getStack());
                  case GAS_OUT -> machine.outputGasTank() == null ? null : GaugeElement.Content.of(machine.outputGasTank().getStack());
                  default -> null;
               };
            }
            UiElementRenderer.render(graphics, element, this.leftPos, this.topPos, storage,
                  this.progressRatio(), this.steamRatio(), content);
         }
      }
   }

   public float storageRatio() {
      return ((MachineMenu)this.menu).isSteam() ? this.steamRatio() : this.energyRatio();
   }

   public MachineLevel machineLevel() {
      return ((MachineMenu)this.menu).level();
   }

   public boolean steamMachine() {
      return ((MachineMenu)this.menu).isSteam();
   }

   public float progressRatio() {
      int total = ((MachineMenu)this.menu).data(3);
      return total <= 0 ? 0.0F : Mth.clamp((float)((MachineMenu)this.menu).data(2) / (float)total, 0.0F, 1.0F);
   }

   public float energyRatio() {
      MachineLevel level = ((MachineMenu)this.menu).level();
      return level != null && level.energyCapacity() > 0L
         ? Mth.clamp((float)((MachineMenu)this.menu).data(0) / (float)level.energyCapacity(), 0.0F, 1.0F)
         : 0.0F;
   }

   public float steamRatio() {
      MachineLevel level = ((MachineMenu)this.menu).level();
      return level != null && level.steamCapacity() > 0L ? Mth.clamp((float)((MachineMenu)this.menu).data(1) / (float)level.steamCapacity(), 0.0F, 1.0F) : 0.0F;
   }

   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      super.render(graphics, mouseX, mouseY, partialTick);
      int warningCode = ((MachineMenu)this.menu).data(MachineMenu.DATA_WARNING);
      this.tabs.renderTabs(graphics, this.leftPos, this.topPos,
            ((MachineMenu)this.menu).data(MachineMenu.DATA_REDSTONE),
            ((MachineMenu)this.menu).data(MachineMenu.DATA_PRIVATE) == 1, warningCode);
      if (this.transporter.isOpen()) {
         this.transporter.render(graphics, this.font, this.leftPos, this.topPos, this.transporterContext());
      } else {
         this.tabs.renderOverlay(graphics, this.font, this.leftPos, this.topPos, this.configContext());
      }

      if (this.editor != null) {
         this.editor.render(graphics, mouseX, mouseY, partialTick);
      }
      // AbstractContainerScreen 1.21 不再自动调用 renderTooltip，必须自己触发。
      this.renderTooltip(graphics, mouseX, mouseY);
   }

   protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
      if (((MachineMenu)this.menu).usesSteamLayout()) {
         // 合金炉/蒸汽机器：标题在顶部水平居中。
         int titleX = (this.imageWidth - this.font.width(this.title)) / 2;
         graphics.drawString(this.font, this.title, titleX, 6, 4210752, false);
      } else {
         graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 4210752, false);
      }
      graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 4210752, false);
   }

   protected void renderSlot(GuiGraphics graphics, Slot slot) {
      // 配置/传输窗口是模态的：打开时隐藏所有物品图标，避免从窗口边缘/底层透出。
      if (!this.tabs.isConfigOpen() && !this.transporter.isOpen()) {
         super.renderSlot(graphics, slot);
      }
   }

   protected void renderSlotHighlight(GuiGraphics graphics, Slot slot, int mouseX, int mouseY, float partialTick) {
      if (!this.tabs.isConfigOpen() && !this.transporter.isOpen()) {
         super.renderSlotHighlight(graphics, slot, mouseX, mouseY, partialTick);
      }
   }

   protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
      if (this.transporter.isOpen()) {
         this.transporter.renderTooltip(graphics, this.font, mouseX, mouseY,
               this.leftPos, this.topPos, this.transporterContext());
         return;
      }
      if (this.tabs.renderTooltip(graphics, this.font, mouseX, mouseY,
            this.leftPos, this.topPos, this.configContext())) {
         return;
      }
      if (((MachineMenu)this.menu).isSteam() && ChemicalBarElement.isOverBar((double)mouseX, (double)mouseY, this.leftPos, this.topPos)) {
         ChemicalBarElement.renderTooltip(graphics, this.font, this.steamStack(), mouseX, mouseY);
         return;
      }
      if (((MachineMenu)this.menu).isSteam() && ChemicalBarElement.isOverDump((double)mouseX, (double)mouseY, this.leftPos, this.topPos)) {
         graphics.renderTooltip(this.font, Component.translatable("gui.meknh_core.dump_steam"), mouseX, mouseY);
         return;
      }
      if (((MachineMenu)this.menu).usesSteamLayout() && !((MachineMenu)this.menu).isSteam()
            && mouseX >= this.leftPos + 164 && mouseX < this.leftPos + 170
            && mouseY >= this.topPos + 16 && mouseY < this.topPos + 70) {
         MachineLevel level = ((MachineMenu)this.menu).level();
         long capacity = level == null ? 0L : level.energyCapacity();
         graphics.renderComponentTooltip(this.font, java.util.List.of(
               Component.translatable("gui.meknh_core.energy_stored",
                     c_y.meknh_core.common.util.EnergyText.shortFe(
                           ((MachineMenu)this.menu).data(MachineMenu.DATA_ENERGY)),
                     c_y.meknh_core.common.util.EnergyText.shortFe(capacity))), mouseX, mouseY);
         return;
      }
      int warningCode = ((MachineMenu)this.menu).data(MachineMenu.DATA_WARNING);
      TabStrip.Tab tab = TabStrip.tabAt(mouseX, mouseY, this.leftPos, this.topPos, warningCode != 0);
      if (tab != null) {
         switch (tab) {
            case CONFIG -> graphics.renderTooltip(this.font, Component.translatable("gui.meknh_core.tab.config"), mouseX, mouseY);
            case TRANSPORTER -> graphics.renderTooltip(this.font, Component.translatable("gui.meknh_core.tab.transporter"), mouseX, mouseY);
            case SECURITY -> graphics.renderTooltip(this.font, Component.translatable(
                  ((MachineMenu)this.menu).data(19) == 1 ? "gui.meknh_core.security.private" : "gui.meknh_core.security.public"
            ), mouseX, mouseY);
            case REDSTONE -> graphics.renderTooltip(this.font, RedstoneMode.byOrdinal(((MachineMenu)this.menu).data(18)).displayName(), mouseX, mouseY);
            case ENERGY -> graphics.renderComponentTooltip(this.font, this.energyTabTooltip(), mouseX, mouseY);
            case WARNING -> graphics.renderComponentTooltip(this.font, this.warningTooltip(warningCode), mouseX, mouseY);
         }
         return;
      }

      if (this.isOverProgress(mouseX, mouseY)) {
         graphics.renderTooltip(this.font, Component.translatable("gui.meknh_core.show_recipe"), mouseX, mouseY);
         return;
      }

      if (this.isOverMekSlot(mouseX, mouseY)) {
         MachineBlockEntity machine = ((MachineMenu)this.menu).machine();
         if (machine != null) {
            if (machine.outputGasTank() != null && !machine.outputGasTank().isEmpty()) {
               graphics.renderTooltip(this.font, Component.literal(
                     machine.outputGasTank().getStack().getChemical().getTextComponent().getString()
                           + "：" + machine.outputGasTank().getStored() + " / "
                           + machine.outputGasTank().getCapacity() + " mB"), mouseX, mouseY);
            } else if (machine.outputFluidTank() != null && !machine.outputFluidTank().isEmpty()) {
               graphics.renderTooltip(this.font, Component.literal(
                     machine.outputFluidTank().getFluid().getHoverName().getString()
                           + "：" + machine.outputFluidTank().getFluidAmount() + " / "
                           + machine.outputFluidTank().getCapacity() + " mB"), mouseX, mouseY);
            } else {
               graphics.renderTooltip(this.font, Component.translatable("gui.meknh_core.empty"), mouseX, mouseY);
            }
         }
         return;
      }

      // 流体/化学品槽：有内容时显示名称 + mB，空槽不显示。
      if (this.renderTankTooltip(graphics, mouseX, mouseY)) {
         return;
      }

      if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
         int index = ((MachineMenu)this.menu).slots.indexOf(this.hoveredSlot);
         if (index >= 0 && index < ((MachineMenu)this.menu).machineSlotCount()) {
            graphics.renderTooltip(this.font, this.hoveredSlot.getItem().getHoverName(), mouseX, mouseY);
            return;
         }
      }

      super.renderTooltip(graphics, mouseX, mouseY);
   }

   /**
    * 流体/化学品竖向槽的 tooltip：有内容时显示名称 + mB，空槽不显示。
    */
   private boolean renderTankTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
      if (((MachineMenu)this.menu).definition() == null) {
         return false;
      }
      List<UiElement> elements = MachineUiLayouts.current(((MachineMenu)this.menu).definition().id());
      if (elements == null || elements.isEmpty()) {
         elements = MachineUiLayouts.defaults(((MachineMenu)this.menu).definition(), ((MachineMenu)this.menu).level());
      }
      for (UiElement element : elements) {
         int x = this.leftPos + element.x();
         int y = this.topPos + element.y();
         if (mouseX < x || mouseX >= x + element.width() || mouseY < y || mouseY >= y + element.height()) {
            continue;
         }
         int amountIndex;
         int idIndex;
         boolean fluid;
         switch (element.kind()) {
            case ENERGY -> {
               MachineLevel level = ((MachineMenu)this.menu).level();
               long capacity = level == null ? 0L : level.energyCapacity();
               graphics.renderComponentTooltip(this.font, java.util.List.of(
                     Component.translatable("gui.meknh_core.energy_stored",
                           c_y.meknh_core.common.util.EnergyText.shortFe(
                                 ((MachineMenu)this.menu).data(MachineMenu.DATA_ENERGY)),
                           c_y.meknh_core.common.util.EnergyText.shortFe(capacity))), mouseX, mouseY);
               return true;
            }
            case FLUID_IN -> {
               amountIndex = 4;
               idIndex = 5;
               fluid = true;
            }
            case FLUID_OUT -> {
               amountIndex = 6;
               idIndex = 7;
               fluid = true;
            }
            case GAS_IN -> {
               amountIndex = 8;
               idIndex = 9;
               fluid = false;
            }
            case GAS_OUT -> {
               amountIndex = 10;
               idIndex = 11;
               fluid = false;
            }
            default -> {
               continue;
            }
         }
         int amount = ((MachineMenu)this.menu).data(amountIndex);
         int id = ((MachineMenu)this.menu).data(idIndex);
         if (amount <= 0 || id < 0) {
            return true;
         }
         Component name;
         long capacity;
         if (fluid) {
            Fluid value = BuiltInRegistries.FLUID.byId(id);
            if (value == null) {
               return true;
            }
            name = new net.neoforged.neoforge.fluids.FluidStack(value, amount).getHoverName();
            capacity = ((MachineMenu)this.menu).fluidCapacity(
                  element.kind() == UiElementKind.FLUID_IN);
         } else {
            Chemical value = MekanismAPI.CHEMICAL_REGISTRY.byId(id);
            if (value == null) {
               return true;
            }
            name = value.getTextComponent();
            capacity = ((MachineMenu)this.menu).chemicalCapacity(
                  element.kind() == UiElementKind.GAS_IN);
         }
         graphics.renderTooltip(this.font, Component.literal(name.getString() + "：" + amount
               + (capacity > 0L ? " / " + capacity : "") + " mB"), mouseX, mouseY);
         return true;
      }
      return false;
   }

   private boolean hasMekOutputSlot() {
      return ((MachineMenu)this.menu).definition() != null
            && !((MachineMenu)this.menu).isSteam()
            && ((MachineMenu)this.menu).definition().id().equals(c_y.meknh_core.MekNHCore.rl("extractor"));
   }

   private int mekSlotX() {
      int inputSlots = ((MachineMenu)this.menu).definition().itemInputSlots();
      int outputSlots = ((MachineMenu)this.menu).definition().itemOutputSlots();
      return this.leftPos + SteamGuiLayout.outputX(inputSlots, outputSlots, Math.max(0, outputSlots - 1))
            + SteamGuiLayout.SLOT_SIZE + SteamGuiLayout.GAP;
   }

   private boolean isOverMekSlot(double mouseX, double mouseY) {
      if (!this.hasMekOutputSlot()) {
         return false;
      }
      int x = this.mekSlotX();
      int y = this.topPos + 16;
      return mouseX >= x - 1 && mouseX < x + 19 && mouseY >= y - 1 && mouseY < y + 59;
   }

   private boolean isOverProgress(int mouseX, int mouseY) {
      if (((MachineMenu)this.menu).definition() == null) {
         return false;
      }
      if (((MachineMenu)this.menu).usesSteamLayout()) {
         int inputSlots = ((MachineMenu)this.menu).definition().itemInputSlots();
         int outputSlots = ((MachineMenu)this.menu).definition().itemOutputSlots();
         int x = this.leftPos + SteamGuiLayout.progressX(inputSlots, outputSlots);
         int y = this.topPos + SteamGuiLayout.PROGRESS_Y;
         return mouseX >= x && mouseX < x + SteamGuiLayout.PROGRESS_WIDTH
               && mouseY >= y && mouseY < y + SteamGuiLayout.PROGRESS_HEIGHT;
      }
      List<UiElement> elements = MachineUiLayouts.current(((MachineMenu)this.menu).definition().id());
      if (elements == null || elements.isEmpty()) {
         elements = MachineUiLayouts.defaults(((MachineMenu)this.menu).definition(), ((MachineMenu)this.menu).level());
      }
      for (UiElement element : elements) {
         if (element.kind() != UiElementKind.PROGRESS) {
            continue;
         }
         int x = this.leftPos + element.x();
         int y = this.topPos + element.y();
         if (mouseX >= x && mouseX < x + element.width()
               && mouseY >= y && mouseY < y + element.height()) {
            return true;
         }
      }
      return false;
   }

   private java.util.List<Component> energyTabTooltip() {
      if (((MachineMenu)this.menu).isSteam()) {
         return this.steamEnergyTooltip();
      }
      MachineLevel level = ((MachineMenu)this.menu).level();
      long capacity = level == null ? 0L : level.energyCapacity();
      long stored = ((MachineMenu)this.menu).data(MachineMenu.DATA_ENERGY);
      java.util.List<Component> tooltip = new java.util.ArrayList<>(3);
      tooltip.add(Component.translatable("gui.meknh_core.energy_using",
            this.energyComponent(((MachineMenu)this.menu).data(MachineMenu.DATA_ENERGY_PER_TICK))));
      tooltip.add(Component.translatable("gui.meknh_core.energy_needed",
            this.energyComponent(Math.max(0L, capacity - stored))));
      tooltip.add(Component.translatable("gui.meknh_core.energy_unit_label",
            Component.translatable(this.energyUnit == 1 ? "gui.meknh_core.energy_unit.j" : "gui.meknh_core.energy_unit.fe")));
      return tooltip;
   }

   /**
    * 能量数值：走 Mek 的 SI 前缀（k / M / G …），并按当前单位开关在 FE / J 之间切换。
    */
   private Component energyComponent(long fe) {
      return this.energyUnit == 1
            ? c_y.meknh_core.common.util.EnergyText.shortJ(Math.round(fe * 2.5D))
            : c_y.meknh_core.common.util.EnergyText.shortFe(fe);
   }

   private java.util.List<Component> steamEnergyTooltip() {
      MachineLevel level = ((MachineMenu)this.menu).level();
      long capacity = level == null ? 0L : level.steamCapacity();
      long stored = ((MachineMenu)this.menu).data(MachineMenu.DATA_STEAM);
      java.util.List<Component> tooltip = new java.util.ArrayList<>(3);
      switch (this.steamEnergyUnit) {
         case 1 -> tooltip.add(Component.translatable("gui.meknh_core.steam_stored_b", decimal(stored / 1000.0D), decimal(capacity / 1000.0D)));
         case 2 -> {
            double storedPercent = capacity <= 0L ? 0.0D : stored * 100.0D / capacity;
            tooltip.add(Component.translatable("gui.meknh_core.steam_stored_percent", decimal(storedPercent)));
         }
         default -> tooltip.add(Component.translatable("gui.meknh_core.steam_stored", format(stored), format(capacity)));
      }
      if (level != null && level.steam() != null) {
         tooltip.add(Component.translatable("gui.meknh_core.steam_ratio",
               level.steam() == c_y.meknh_core.common.machine.steam.SteamVariant.HIGH ? 2 : 1));
      }
      tooltip.add(Component.translatable("gui.meknh_core.energy_unit",
            Component.translatable("gui.meknh_core.energy_unit." + switch (this.steamEnergyUnit) {
               case 1 -> "b";
               case 2 -> "percent";
               default -> "mb";
            })));
      return tooltip;
   }

   private java.util.List<Component> warningTooltip(int code) {
      java.util.List<Component> tooltip = new java.util.ArrayList<>();
      tooltip.add(Component.translatable("gui.meknh_core.tab.warning"));
      if ((code & 1) != 0) {
         tooltip.add(Component.translatable(((MachineMenu)this.menu).isSteam()
               ? "gui.meknh_core.warning.no_steam"
               : "gui.meknh_core.warning.no_energy"));
      }
      if ((code & 2) != 0) {
         tooltip.add(Component.translatable("gui.meknh_core.warning.no_recipe"));
      }
      if ((code & 4) != 0) {
         tooltip.add(Component.translatable("gui.meknh_core.warning.output_full"));
      }
      if ((code & 8) != 0) {
         tooltip.add(Component.translatable("gui.meknh_core.warning.redstone"));
      }
      return tooltip;
   }

   private static String format(long value) {
      return String.format("%,d", value);
   }

   private static String decimal(double value) {
      return String.format(Locale.ROOT, "%.2f", value);
   }

   /**
    * 横向条显示蒸汽能量罐：存量来自每 tick 同步的 {@code DATA_STEAM}。
    */
   private ChemicalStack steamStack() {
      int amount = ((MachineMenu)this.menu).data(MachineMenu.DATA_STEAM);
      if (amount <= 0) {
         return ChemicalStack.EMPTY;
      }
      return MekanismChemicals.STEAM.asStack(amount);
   }

   private void sendButton(int id) {
      if (this.minecraft != null && this.minecraft.gameMode != null) {
         this.minecraft.gameMode.handleInventoryButtonClick(((MachineMenu)this.menu).containerId, id);
      }
   }

   private void cycleEnergyUnit(int button) {
      if (((MachineMenu)this.menu).isSteam()) {
         this.steamEnergyUnit = button == 0
               ? (this.steamEnergyUnit + 1) % 3
               : Math.floorMod(this.steamEnergyUnit - 1, 3);
      } else {
         this.energyUnit = button == 0
               ? (this.energyUnit + 1) % 2
               : Math.floorMod(this.energyUnit - 1, 2);
      }
   }

   private SideConfigPanel.Context configContext() {
      return new SideConfigPanel.Context() {
         @Override
         public int sideMode(int type, Direction side) {
            return ((MachineMenu)MachineScreen.this.menu).data(
                  ((MachineMenu)MachineScreen.this.menu).sideDataBase(type) + side.ordinal());
         }

         @Override
         public boolean supports(int type) {
            return ((MachineMenu)MachineScreen.this.menu).supportsConfigType(type);
         }

         @Override
         public boolean autoEject(int type) {
            return ((MachineMenu)MachineScreen.this.menu).data(MachineMenu.DATA_AUTO_EJECT + type) != 0;
         }

         @Override
         public boolean canEject(int type) {
            MachineBlockEntity machine = ((MachineMenu)MachineScreen.this.menu).machine();
            return machine != null && machine.canEjectSideType(
                  c_y.meknh_core.common.machine.config.MachineConfigType.VALUES[type]);
         }

         @Override
         public boolean allowsMode(int type, c_y.meknh_core.common.machine.config.SideMode mode) {
            MachineBlockEntity machine = ((MachineMenu)MachineScreen.this.menu).machine();
            return machine != null && machine.allowsSideMode(
                  c_y.meknh_core.common.machine.config.MachineConfigType.VALUES[type], mode);
         }

         @Override
         public Direction facing() {
            return MachineScreen.this.facing();
         }

         @Override
         public net.minecraft.core.BlockPos pos() {
            return MachineScreen.this.machinePos();
         }

         @Override
         public net.minecraft.world.level.Level level() {
            return MachineScreen.this.minecraft == null ? null : MachineScreen.this.minecraft.level;
         }

         @Override
         public void sendButton(int id) {
            MachineScreen.this.sendButton(id);
         }
      };
   }

   private TransporterPanel.Context transporterContext() {
      return new TransporterPanel.Context() {
         @Override
         public int strictInput() {
            return ((MachineMenu)MachineScreen.this.menu).data(MachineMenu.DATA_STRICT_INPUT);
         }

         @Override
         public int outputColor() {
            return ((MachineMenu)MachineScreen.this.menu).data(MachineMenu.DATA_OUTPUT_COLOR);
         }

         @Override
         public int inputColor(Direction direction) {
            return ((MachineMenu)MachineScreen.this.menu).data(MachineMenu.DATA_INPUT_COLOR + direction.ordinal());
         }

         @Override
         public Direction facing() {
            return MachineScreen.this.facing();
         }

         @Override
         public void sendButton(int id) {
            MachineScreen.this.sendButton(id);
         }
      };
   }



   private Direction facing() {
      MachineBlockEntity machine = ((MachineMenu)this.menu).machine();
      if (machine == null) {
         return Direction.NORTH;
      } else {
         BlockState state = machine.getBlockState();
         return state.hasProperty(MachineBlock.FACING) ? (Direction)state.getValue(MachineBlock.FACING) : Direction.NORTH;
      }
   }


   /**
    * 与 Mekanism RelativeSide 一致：顶部/底部/前面/后面/左面/右面。
    */

   @Nullable



   private net.minecraft.core.BlockPos machinePos() {
      MachineBlockEntity machine = ((MachineMenu)this.menu).machine();
      return machine == null ? this.minecraft.player.blockPosition() : machine.getBlockPos();
   }








   @Nullable

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (this.transporter.isOpen()) {
         return this.transporter.mouseClicked(mouseX, mouseY, button, this.leftPos, this.topPos,
               this.transporterContext());
      }
      int warning = ((MachineMenu)this.menu).data(MachineMenu.DATA_WARNING);
      if (this.tabs.isConfigOpen()) {
         return this.tabs.mouseClicked(mouseX, mouseY, button, this.leftPos, this.topPos,
               warning != 0, this::sendButton, this.configContext());
      }
      if (this.handleDropper(mouseX, mouseY, button)) {
         return true;
      }
      if (((MachineMenu)this.menu).isSteam()
            && ChemicalBarElement.isOverDump(mouseX, mouseY, this.leftPos, this.topPos)) {
         if (button == 0) {
            this.sendButton(MachineMenu.BUTTON_DUMP_STEAM);
         }
         return true;
      }
      if (this.tabs.mouseClicked(mouseX, mouseY, button, this.leftPos, this.topPos,
            warning != 0, this::sendButton, this.configContext())) {
         return true;
      }
      // 点击进度条：打开该机器的 JEI 配方页（不再显示进度百分比）。
      if (button == 0 && this.isOverProgress((int)mouseX, (int)mouseY)) {
         if (((MachineMenu)this.menu).definition() != null
               && c_y.meknh_core.client.jei.MeknhJeiPlugin.openMachineRecipes(
                     ((MachineMenu)this.menu).definition().id())) {
            return true;
         }
      }
      return this.editor != null && this.editor.mouseClicked(mouseX, mouseY, button)
            ? true
            : super.mouseClicked(mouseX, mouseY, button);
   }


   public java.util.Optional<? extends mezz.jei.api.runtime.IClickableIngredient<?>> jeiTankIngredient(
         mezz.jei.api.gui.builder.IClickableIngredientFactory factory, double mouseX, double mouseY) {
      MachineBlockEntity machine = ((MachineMenu)this.menu).machine();
      if (machine == null) {
         return java.util.Optional.empty();
      }
      if (((MachineMenu)this.menu).isSteam()
            && ChemicalBarElement.isOverBar(mouseX, mouseY, this.leftPos, this.topPos)) {
         return machine.steamTank() == null ? java.util.Optional.empty()
               : this.chemicalIngredient(factory, machine.steamTank().getStack(),
               this.leftPos + ChemicalBarElement.X, this.topPos + ChemicalBarElement.Y,
               ChemicalBarElement.FRAME_WIDTH, ChemicalBarElement.FRAME_HEIGHT);
      }
      if (this.hasMekOutputSlot() && this.isOverMekSlot(mouseX, mouseY)) {
         int x = this.mekSlotX();
         int y = this.topPos + 16;
         if (machine.outputGasTank() != null && !machine.outputGasTank().isEmpty()) {
            return this.chemicalIngredient(factory, machine.outputGasTank().getStack(), x, y, 18, 58);
         }
         if (machine.outputFluidTank() != null && !machine.outputFluidTank().getFluid().isEmpty()) {
            return this.fluidIngredient(factory, machine.outputFluidTank().getFluid(), x, y, 18, 58);
         }
         return java.util.Optional.empty();
      }
      if (((MachineMenu)this.menu).definition() == null) {
         return java.util.Optional.empty();
      }
      List<UiElement> elements = MachineUiLayouts.current(((MachineMenu)this.menu).definition().id());
      if (elements == null || elements.isEmpty()) {
         elements = MachineUiLayouts.defaults(((MachineMenu)this.menu).definition(), ((MachineMenu)this.menu).level());
      }
      for (UiElement element : elements) {
         int x = this.leftPos + element.x();
         int y = this.topPos + element.y();
         if (mouseX < x || mouseX >= x + element.width() || mouseY < y || mouseY >= y + element.height()) {
            continue;
         }
         switch (element.kind()) {
            case STEAM -> {
               if (machine.steamTank() != null) {
                  return this.chemicalIngredient(factory, machine.steamTank().getStack(),
                        x, y, element.width(), element.height());
               }
            }
            case GAS_IN -> {
               if (machine.inputGasTank() != null) {
                  return this.chemicalIngredient(factory, machine.inputGasTank().getStack(),
                        x, y, element.width(), element.height());
               }
            }
            case GAS_OUT -> {
               if (machine.outputGasTank() != null) {
                  return this.chemicalIngredient(factory, machine.outputGasTank().getStack(),
                        x, y, element.width(), element.height());
               }
            }
            case FLUID_IN -> {
               if (machine.inputFluidTank() != null) {
                  return this.fluidIngredient(factory, machine.inputFluidTank().getFluid(),
                        x, y, element.width(), element.height());
               }
            }
            case FLUID_OUT -> {
               if (machine.outputFluidTank() != null) {
                  return this.fluidIngredient(factory, machine.outputFluidTank().getFluid(),
                        x, y, element.width(), element.height());
               }
            }
            default -> {
            }
         }
      }
      return java.util.Optional.empty();
   }

   private java.util.Optional<? extends mezz.jei.api.runtime.IClickableIngredient<?>> chemicalIngredient(
         mezz.jei.api.gui.builder.IClickableIngredientFactory factory,
         ChemicalStack stack, int x, int y, int width, int height) {
      if (stack.isEmpty()) {
         return java.util.Optional.empty();
      }
      return factory.createBuilder(mekanism.client.recipe_viewer.jei.MekanismJEI.TYPE_CHEMICAL, stack)
            .buildWithArea(x, y, width, height);
   }

   private java.util.Optional<? extends mezz.jei.api.runtime.IClickableIngredient<?>> fluidIngredient(
         mezz.jei.api.gui.builder.IClickableIngredientFactory factory,
         net.neoforged.neoforge.fluids.FluidStack stack, int x, int y, int width, int height) {
      if (stack.isEmpty()) {
         return java.util.Optional.empty();
      }
      return factory.createBuilder(mezz.jei.api.neoforge.NeoForgeTypes.FLUID_STACK, stack)
            .buildWithArea(x, y, width, height);
   }

   private boolean handleDropper(double mouseX, double mouseY, int button) {
      if (!c_y.meknh_core.common.gui.element.DropperInteraction.isDropper()) {
         return false;
      }
      MachineBlockEntity machine = ((MachineMenu)this.menu).machine();
      if (machine == null) {
         return false;
      }
      net.minecraft.core.BlockPos pos = machine.getBlockPos();
      if (((MachineMenu)this.menu).isSteam()
            && ChemicalBarElement.isOverBar(mouseX, mouseY, this.leftPos, this.topPos)) {
         return c_y.meknh_core.common.gui.element.DropperInteraction.click(pos,
               c_y.meknh_core.common.network.DropperUsePayload.TANK_CHEMICAL, 0,
               button, Screen.hasShiftDown());
      }
      if (this.hasMekOutputSlot() && this.isOverMekSlot(mouseX, mouseY)) {
         boolean gas = machine.outputGasTank() != null && !machine.outputGasTank().isEmpty();
         return c_y.meknh_core.common.gui.element.DropperInteraction.click(pos,
               gas ? c_y.meknh_core.common.network.DropperUsePayload.TANK_CHEMICAL
                   : c_y.meknh_core.common.network.DropperUsePayload.TANK_FLUID,
               gas ? 2 : 1, button, Screen.hasShiftDown());
      }
      if (((MachineMenu)this.menu).definition() == null) {
         return false;
      }
      List<UiElement> elements = MachineUiLayouts.current(((MachineMenu)this.menu).definition().id());
      if (elements == null || elements.isEmpty()) {
         elements = MachineUiLayouts.defaults(((MachineMenu)this.menu).definition(), ((MachineMenu)this.menu).level());
      }
      for (UiElement element : elements) {
         int tankType;
         int tankId;
         switch (element.kind()) {
            case STEAM -> {
               tankType = c_y.meknh_core.common.network.DropperUsePayload.TANK_CHEMICAL;
               tankId = 0;
            }
            case GAS_IN -> {
               tankType = c_y.meknh_core.common.network.DropperUsePayload.TANK_CHEMICAL;
               tankId = 1;
            }
            case GAS_OUT -> {
               tankType = c_y.meknh_core.common.network.DropperUsePayload.TANK_CHEMICAL;
               tankId = 2;
            }
            case FLUID_IN -> {
               tankType = c_y.meknh_core.common.network.DropperUsePayload.TANK_FLUID;
               tankId = 0;
            }
            case FLUID_OUT -> {
               tankType = c_y.meknh_core.common.network.DropperUsePayload.TANK_FLUID;
               tankId = 1;
            }
            default -> {
               continue;
            }
         }
         int x = this.leftPos + element.x();
         int y = this.topPos + element.y();
         if (mouseX >= x && mouseX < x + element.width() && mouseY >= y && mouseY < y + element.height()) {
            return c_y.meknh_core.common.gui.element.DropperInteraction.click(pos, tankType, tankId,
                  button, Screen.hasShiftDown());
         }
      }
      return false;
   }

   public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
      if (this.tabs.mouseDragged(mouseX, mouseY, button)) {
         return true;
      }
      return this.editor != null && this.editor.mouseDragged(mouseX, mouseY, button, dragX, dragY)
         ? true
         : super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      this.tabs.mouseReleased();
      if (this.editor != null) {
         this.editor.mouseReleased();
      }

      return super.mouseReleased(mouseX, mouseY, button);
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (this.tabs.keyPressed(keyCode)) {
         return true;
      }
      if (this.transporter.keyPressed(keyCode)) {
         return true;
      }
      return this.editor != null && this.editor.keyPressed(keyCode) ? true : super.keyPressed(keyCode, scanCode, modifiers);
   }

   public void onClose() {
      if (this.editor != null) {
         this.editor.saveNow();
      }

      super.onClose();
   }

   public void removed() {
   }

   public int guiLeft() {
      return this.leftPos;
   }

   public int guiTop() {
      return this.topPos;
   }

   public boolean hasEditorOverlay() {
      return this.editor != null;
   }

   public Font editorFont() {
      return this.font;
   }

   public List<Renderable> editorRenderables() {
      return this.renderables;
   }

   public Button addEditorButton(Component text, Runnable action, int x, int y, int width, int height) {
      Button button = Button.builder(text, b -> action.run()).bounds(x, y, width, height).build();
      this.addRenderableWidget(button);
      return button;
   }

   public void openEditorScreen(Screen screen) {
      if (this.minecraft != null) {
         this.minecraft.setScreen(screen);
      }
   }

   public <T extends GuiEventListener & Renderable & NarratableEntry> T addEditorWidget(T widget) {
      return (T)this.addRenderableWidget(widget);
   }

   public void notifyPlayer(Component message) {
      if (this.minecraft != null && this.minecraft.player != null) {
         this.minecraft.player.displayClientMessage(message, false);
      }
   }

   public void openTexturePicker(UiElement element, Consumer<ResourceLocation> onPick) {
      if (this.minecraft != null) {
         this.minecraft.setScreen(new TexturePickerScreen(this, element, onPick));
      }
   }

   private boolean isPlayerSlot(Slot slot) {
      return slot.index >= ((MachineMenu)this.menu).machineSlotCount();
   }
}
