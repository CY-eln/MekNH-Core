package c_y.meknh_core.common.generator.boiler;

import c_y.meknh_core.common.gui.element.ScreenTabs;
import c_y.meknh_core.common.gui.element.SideConfigPanel;
import c_y.meknh_core.common.gui.element.TabStrip;
import c_y.meknh_core.common.gui.element.TransporterPanel;
import c_y.meknh_core.common.gui.layout.MachineGuiTextures;
import c_y.meknh_core.common.gui.layout.MekGuiTextures;
import c_y.meknh_core.common.gui.render.GuiDraw;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.GuiUtils.TilingDirection;
import mekanism.client.render.MekanismRenderer;
import mekanism.client.render.MekanismRenderer.FluidTextureType;
import mekanism.common.registries.MekanismChemicals;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * 锅炉界面：标题、热量条、燃烧条、水/燃料/蒸汽罐、通用 Tab 与侧配置。
 */
public class BoilerScreen extends AbstractContainerScreen<BoilerMenu> {

    private static final int RATE_X = 48;
    private static final int RATE_Y = 18;
    private static final int RATE_WIDTH = 78;
    private static final int RATE_HEIGHT = 8;
    private final ScreenTabs tabs = new ScreenTabs();
    private final TransporterPanel transporter = new TransporterPanel();
    /** 0=mB/t，1=B/t。 */
    private int steamRateUnit;

    public BoilerScreen(BoilerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 73;
    }

    @Override
    protected void init() {
        super.init();
        this.tabs.init(this.menu.blockEntity().getBlockPos());
        this.tabs.setEnergyClick(button -> this.steamRateUnit = button == 0
                ? (this.steamRateUnit + 1) % 2
                : Math.floorMod(this.steamRateUnit - 1, 2));
        this.tabs.setTransporterClick(this.transporter::toggle);
    }

    private SideConfigPanel.Context configContext() {
        return new SideConfigPanel.Context() {
            @Override
            public int sideMode(int type, Direction side) {
                return BoilerScreen.this.menu.value(
                        BoilerScreen.this.menu.sideDataBase(type) + side.ordinal());
            }

            @Override
            public boolean supports(int type) {
                return switch (type) {
                    case 0 -> BoilerScreen.this.menu.isSolid();
                    case 1 -> true; // 水/液体燃料
                    case 2 -> true; // 蒸汽输出/液体燃料化学品
                    default -> false; // 锅炉无能量缓存
                };
            }

            @Override
            public boolean autoEject(int type) {
                return BoilerScreen.this.menu.value(BoilerMenu.DATA_AUTO_EJECT + type) != 0;
            }

            @Override
            public boolean canEject(int type) {
                return BoilerScreen.this.menu.blockEntity().canEjectSideType(
                        c_y.meknh_core.common.machine.config.MachineConfigType.VALUES[type]);
            }

            @Override
            public boolean allowsMode(int type, c_y.meknh_core.common.machine.config.SideMode mode) {
                return BoilerScreen.this.menu.blockEntity().allowsSideMode(
                        c_y.meknh_core.common.machine.config.MachineConfigType.VALUES[type], mode);
            }

            @Override
            public Direction facing() {
                BlockState state = BoilerScreen.this.menu.blockEntity().getBlockState();
                return state.hasProperty(BoilerBlock.FACING) ? state.getValue(BoilerBlock.FACING) : Direction.NORTH;
            }

            @Override
            public BlockPos pos() {
                return BoilerScreen.this.menu.blockEntity().getBlockPos();
            }

            @Override
            public net.minecraft.world.level.Level level() {
                return BoilerScreen.this.menu.blockEntity().getLevel();
            }

            @Override
            public void sendButton(int id) {
                BoilerScreen.this.sendButton(id);
            }
        };
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        GuiDraw.nineSlice(graphics, MachineGuiTextures.BASE, this.leftPos, this.topPos,
                this.imageWidth, this.imageHeight, 4, 256, 256);
        boolean active = this.menu.value(BoilerMenu.DATA_ACTIVE) != 0;
        boolean blocked = this.menu.value(BoilerMenu.DATA_BLOCKED) != 0;

        // 玩家背包槽位底图。
        for (Slot slot : this.menu.slots) {
            if (slot.container instanceof Inventory) {
                graphics.blit(MachineGuiTextures.SLOT,
                        this.leftPos + slot.x - 1, this.topPos + slot.y - 1,
                        18, 18, 0, 0, 18, 18, 18, 18);
            }
        }
        if (this.menu.isSolid()) {
            // Mekanism gui/progress/flame.png 双态图：左态未燃烧，右态燃烧中。
            int burn = this.menu.value(BoilerMenu.DATA_BURN_TIME);
            int duration = Math.max(1, this.menu.value(BoilerMenu.DATA_BURN_DURATION));
            graphics.blit(MekGuiTextures.FLAME, this.leftPos + 48, this.topPos + 34,
                    0, 0, 13, 13, 26, 13);
            int flameHeight = Math.round(13.0F * Math.min(1.0F, burn / (float)duration));
            if (active && flameHeight > 0) {
                graphics.blit(MekGuiTextures.FLAME, this.leftPos + 48, this.topPos + 34 + 13 - flameHeight,
                        13, 13 - flameHeight, 13, flameHeight, 26, 13);
            }
            // 燃烧条下方的输入槽。
            graphics.blit(MachineGuiTextures.SLOT_INPUT,
                    this.leftPos + 46, this.topPos + 50,
                    18, 18, 0, 0, 18, 18, 18, 18);
        }

        boolean solid = this.menu.isSolid();
        boolean solar = this.menu.isSolar();
        boolean liquid = this.menu.isLiquid();

        // 固体/液体锅炉：水槽始终在热量条下方。
        if (solid || liquid) {
            GuiDraw.nineSliceXY(graphics, MekGuiTextures.SLOT_INPUT, this.leftPos + 80, this.topPos + 40,
                    16, 28, 1, 1, 18, 18, 0, 0, 18, 18);
            this.renderContents(graphics, 80, 40, 16, 28,
                    this.menu.value(BoilerMenu.DATA_WATER), this.menu.value(BoilerMenu.DATA_WATER_CAP),
                    this.menu.waterFluid(), mekanism.api.chemical.ChemicalStack.EMPTY);
            graphics.blit(MekGuiTextures.GAUGE_SHORT, this.leftPos + 80, this.topPos + 40,
                    16, 28, 0, 0, 16, 28, 16, 28);
        }

        if (solar) {
            // 太阳能：水槽是镜像长罐。
            GuiDraw.nineSliceXY(graphics, MekGuiTextures.SLOT_OUTPUT, this.leftPos + 18, this.topPos + 10,
                    18, 58, 1, 1, 18, 18, 0, 0, 18, 18);
            this.renderContents(graphics, 18, 10, 18, 58,
                    this.menu.value(BoilerMenu.DATA_WATER), this.menu.value(BoilerMenu.DATA_WATER_CAP),
                    this.menu.waterFluid(), mekanism.api.chemical.ChemicalStack.EMPTY);
            graphics.blit(MachineGuiTextures.GAUGE, this.leftPos + 19, this.topPos + 10,
                    16, 58, 0, 0, 16, 58, 16, 58);
        } else if (liquid) {
            // 液体：镜像长罐是燃料输入槽。
            GuiDraw.nineSliceXY(graphics, MekGuiTextures.SLOT_INPUT, this.leftPos + 18, this.topPos + 10,
                    18, 58, 1, 1, 18, 18, 0, 0, 18, 18);
            this.renderContents(graphics, 18, 10, 18, 58,
                    this.menu.value(BoilerMenu.DATA_FUEL), this.menu.value(BoilerMenu.DATA_FUEL_CAP),
                    this.menu.fuelFluid(), this.menu.fuelChemical());
            graphics.blit(MachineGuiTextures.GAUGE, this.leftPos + 19, this.topPos + 10,
                    16, 58, 0, 0, 16, 58, 16, 58);
        }

        // 太阳能水槽不在热条下方，用 large_right；其余用 small_right。
        if (this.menu.isSolar()) {
            this.renderArrow(graphics, MekGuiTextures.LARGE_RIGHT, this.leftPos + 64, this.topPos + 35,
                    48, 8, active, blocked);
        } else if (this.menu.isLiquid()) {
            this.renderArrow(graphics, MekGuiTextures.SMALL_RIGHT, this.leftPos + 104, this.topPos + 50,
                    28, 8, active, blocked);
        } else {
            this.renderArrow(graphics, MekGuiTextures.SMALL_RIGHT, this.leftPos + 104, this.topPos + 50,
                    28, 8, active, blocked);
        }

        // 长罐 = 输出槽罐体 + standard.png 刻度。
        GuiDraw.nineSliceXY(graphics, MekGuiTextures.SLOT_OUTPUT, this.leftPos + 140, this.topPos + 10,
                18, 58, 1, 1, 18, 18, 0, 0, 18, 18);
        this.renderContents(graphics, 140, 10, 18, 58,
                this.menu.value(BoilerMenu.DATA_STEAM), this.menu.value(BoilerMenu.DATA_STEAM_CAP),
                net.neoforged.neoforge.fluids.FluidStack.EMPTY, this.menu.outputChemical());
        graphics.blit(MachineGuiTextures.GAUGE, this.leftPos + 141, this.topPos + 10,
                16, 58, 0, 0, 16, 58, 16, 58);

        int temperature = this.menu.value(BoilerMenu.DATA_TEMPERATURE);
        int max = Math.max(1, this.menu.value(BoilerMenu.DATA_TEMPERATURE_MAX));
        float level = Math.min(1.0F, Math.max(0.0F, temperature / (float)max));

        // GuiBar 外框：mekanism:gui/bar/base.png，2px 九宫格。
        GuiDraw.nineSliceXY(graphics, MekGuiTextures.CHEMICAL_BAR,
                this.leftPos + RATE_X, this.topPos + RATE_Y,
                RATE_WIDTH + 2, RATE_HEIGHT + 2,
                2, 2, 5, 5, 0, 0, 5, 5);

        // GuiHorizontalRateBar：mekanism:gui/bar/horizontal_rate.png。
        int display = Math.round(level * RATE_WIDTH);
        if (display > 0) {
            graphics.blit(MekGuiTextures.HORIZONTAL_RATE,
                    this.leftPos + RATE_X + 1, this.topPos + RATE_Y + 1,
                    0, 0, display, RATE_HEIGHT,
                    RATE_WIDTH, RATE_HEIGHT);
        }

        // 配置窗口在 render 末尾作为覆盖层绘制。
    }

    private void sendButton(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    private TransporterPanel.Context transporterContext() {
        return new TransporterPanel.Context() {
            @Override
            public int strictInput() {
                return BoilerScreen.this.menu.value(BoilerMenu.DATA_STRICT_INPUT);
            }

            @Override
            public int outputColor() {
                return BoilerScreen.this.menu.value(BoilerMenu.DATA_OUTPUT_COLOR);
            }

            @Override
            public int inputColor(Direction direction) {
                return BoilerScreen.this.menu.value(BoilerMenu.DATA_INPUT_COLOR + direction.ordinal());
            }

            @Override
            public Direction facing() {
                BlockState state = BoilerScreen.this.menu.blockEntity().getBlockState();
                return state.hasProperty(BoilerBlock.FACING) ? state.getValue(BoilerBlock.FACING) : Direction.NORTH;
            }

            @Override
            public void sendButton(int id) {
                BoilerScreen.this.sendButton(id);
            }
        };
    }

    private java.util.List<Component> warningTooltip(int code) {
        java.util.List<Component> tooltip = new java.util.ArrayList<>();
        tooltip.add(Component.translatable("gui.meknh_core.tab.warning"));
        if ((code & 1) != 0) {
            tooltip.add(Component.translatable("gui.meknh_core.warning.no_water"));
        }
        if ((code & 2) != 0) {
            tooltip.add(Component.translatable("gui.meknh_core.warning.no_fuel"));
        }
        if ((code & 4) != 0) {
            tooltip.add(Component.translatable("gui.meknh_core.warning.output_full"));
        }
        if ((code & 8) != 0) {
            tooltip.add(Component.translatable("gui.meknh_core.warning.redstone"));
        }
        return tooltip;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.transporter.isOpen()) {
            return this.transporter.mouseClicked(mouseX, mouseY, button, this.leftPos, this.topPos,
                    this.transporterContext());
        }
        int warning = this.menu.value(BoilerMenu.DATA_WARNING);
        if (this.tabs.mouseClicked(mouseX, mouseY, button, this.leftPos, this.topPos, warning != 0,
                this::sendButton, this.configContext())) {
            return true;
        }
        if (this.tabs.isConfigOpen()) {
            return true;
        }
        if (this.handleDropper(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleDropper(double mouseX, double mouseY, int button) {
        if (!c_y.meknh_core.common.gui.element.DropperInteraction.isDropper()) {
            return false;
        }
        net.minecraft.core.BlockPos pos = this.menu.blockEntity().getBlockPos();
        int waterX = this.menu.isSolar() ? 18 : 80;
        int waterY = this.menu.isSolar() ? 10 : 40;
        int waterH = this.menu.isSolar() ? 58 : 28;
        if (mouseX >= this.leftPos + waterX && mouseX < this.leftPos + waterX + 16
                && mouseY >= this.topPos + waterY && mouseY < this.topPos + waterY + waterH) {
            return c_y.meknh_core.common.gui.element.DropperInteraction.click(pos,
                    c_y.meknh_core.common.network.DropperUsePayload.TANK_FLUID, 0, button, Screen.hasShiftDown());
        }
        if (this.menu.isLiquid()
                && mouseX >= this.leftPos + 18 && mouseX < this.leftPos + 36
                && mouseY >= this.topPos + 10 && mouseY < this.topPos + 68) {
            int tankType = this.menu.fuelChemical().isEmpty()
                    ? c_y.meknh_core.common.network.DropperUsePayload.TANK_FLUID
                    : c_y.meknh_core.common.network.DropperUsePayload.TANK_CHEMICAL;
            return c_y.meknh_core.common.gui.element.DropperInteraction.click(pos, tankType, 1,
                    button, Screen.hasShiftDown());
        }
        if (mouseX >= this.leftPos + 140 && mouseX < this.leftPos + 158
                && mouseY >= this.topPos + 10 && mouseY < this.topPos + 68) {
            return c_y.meknh_core.common.gui.element.DropperInteraction.click(pos,
                    c_y.meknh_core.common.network.DropperUsePayload.TANK_CHEMICAL, 1, button, Screen.hasShiftDown());
        }
        return false;
    }

    public java.util.Optional<? extends mezz.jei.api.runtime.IClickableIngredient<?>> jeiTankIngredient(
            mezz.jei.api.gui.builder.IClickableIngredientFactory factory, double mouseX, double mouseY) {
        int waterX = this.menu.isSolar() ? 18 : 80;
        int waterY = this.menu.isSolar() ? 10 : 40;
        int waterH = this.menu.isSolar() ? 58 : 28;
        if (mouseX >= this.leftPos + waterX && mouseX < this.leftPos + waterX + 16
                && mouseY >= this.topPos + waterY && mouseY < this.topPos + waterY + waterH) {
            return this.fluidIngredient(factory, this.menu.waterFluid(),
                    this.leftPos + waterX, this.topPos + waterY, 16, waterH);
        }
        if (this.menu.isLiquid()
                && mouseX >= this.leftPos + 18 && mouseX < this.leftPos + 36
                && mouseY >= this.topPos + 10 && mouseY < this.topPos + 68) {
            if (!this.menu.fuelChemical().isEmpty()) {
                return this.chemicalIngredient(factory, this.menu.fuelChemical(),
                        this.leftPos + 18, this.topPos + 10, 18, 58);
            }
            return this.fluidIngredient(factory, this.menu.fuelFluid(),
                    this.leftPos + 18, this.topPos + 10, 18, 58);
        }
        if (mouseX >= this.leftPos + 140 && mouseX < this.leftPos + 158
                && mouseY >= this.topPos + 10 && mouseY < this.topPos + 68) {
            return this.chemicalIngredient(factory, this.menu.outputChemical(),
                    this.leftPos + 140, this.topPos + 10, 18, 58);
        }
        return java.util.Optional.empty();
    }

    private java.util.Optional<? extends mezz.jei.api.runtime.IClickableIngredient<?>> chemicalIngredient(
            mezz.jei.api.gui.builder.IClickableIngredientFactory factory,
            mekanism.api.chemical.ChemicalStack stack, int x, int y, int width, int height) {
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

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.tabs.keyPressed(keyCode)) {
            return true;
        }
        if (this.transporter.keyPressed(keyCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.tabs.mouseDragged(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.tabs.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot) {
        if (!this.tabs.isConfigOpen() && !this.transporter.isOpen()) {
            super.renderSlot(graphics, slot);
        }
    }

    @Override
    protected void renderSlotHighlight(GuiGraphics graphics, Slot slot, int mouseX, int mouseY, float partialTick) {
        if (!this.tabs.isConfigOpen() && !this.transporter.isOpen()) {
            super.renderSlotHighlight(graphics, slot, mouseX, mouseY, partialTick);
        }
    }

    private void renderArrow(GuiGraphics graphics, net.minecraft.resources.ResourceLocation texture,
                             int x, int y, int width, int height, boolean active, boolean blocked) {
        int textureHeight = height * 3;
        graphics.blit(texture, x, y, width, height, 0, 0, width, height, width, textureHeight);
        if (active) {
            graphics.blit(texture, x, y, width, height, 0, height, width, height, width, textureHeight);
        } else if (blocked) {
            graphics.blit(texture, x, y, width, height, 0, height * 2, width, height, width, textureHeight);
        }
    }

    private void renderContents(GuiGraphics graphics, int x, int y, int width, int height,
                                int amount, int capacity, FluidStack fluid, mekanism.api.chemical.ChemicalStack chemical) {
        if (amount <= 0 || capacity <= 0) {
            return;
        }
        int innerWidth = width - 2;
        int innerHeight = height - 2;
        int fill = Math.max(1, Math.round(innerHeight * Math.min(1.0F, amount / (float)capacity)));
        TextureAtlasSprite sprite = null;
        boolean colored = false;
        if (!chemical.isEmpty()) {
            sprite = MekanismRenderer.getChemicalTexture(chemical);
            int tint = chemical.getChemicalTint();
            graphics.setColor(((tint >> 16) & 0xFF) / 255.0F, ((tint >> 8) & 0xFF) / 255.0F,
                    (tint & 0xFF) / 255.0F, 1.0F);
            colored = true;
        } else if (!fluid.isEmpty()) {
            sprite = MekanismRenderer.getFluidTexture(fluid, FluidTextureType.STILL);
            // 直接使用流体自带纹理与 tint，不额外改色。
            MekanismRenderer.color(graphics, fluid);
            colored = true;
        }
        if (sprite == null) {
            return;
        }
        graphics.enableScissor(this.leftPos + x + 1, this.topPos + y + 1,
                this.leftPos + x + width - 1, this.topPos + y + height - 1);
        GuiUtils.drawTiledSprite(graphics, this.leftPos + x + 1, this.topPos + y + height - 1,
                0, innerWidth, fill, sprite, 16, 16, 0, TilingDirection.DOWN_RIGHT);
        graphics.disableScissor();
        if (colored) {
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {        int titleX = (this.imageWidth - this.font.width(this.title)) / 2;
        graphics.drawString(this.font, this.title, titleX, 6, 4210752, false);
        graphics.drawString(this.font, this.playerInventoryTitle,
                this.inventoryLabelX, this.inventoryLabelY, 4210752, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int warning = this.menu.value(BoilerMenu.DATA_WARNING);
        this.tabs.renderTabs(graphics, this.leftPos, this.topPos,
                this.menu.value(BoilerMenu.DATA_REDSTONE),
                this.menu.value(BoilerMenu.DATA_PRIVATE) == 1, warning);
        if (this.transporter.isOpen()) {
            this.transporter.render(graphics, this.font, this.leftPos, this.topPos, this.transporterContext());
        } else {
            this.tabs.renderOverlay(graphics, this.font, this.leftPos, this.topPos, this.configContext());
        }
        // 1.21 的 AbstractContainerScreen 只在有物品槽时自动走 tooltip，
        // 自定义热量条需要显式触发。
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
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
        int warning = this.menu.value(BoilerMenu.DATA_WARNING);
        TabStrip.Tab tab = TabStrip.tabAt(mouseX, mouseY, this.leftPos, this.topPos, warning != 0);
        if (tab != null) {
            if (tab == TabStrip.Tab.WARNING) {
                graphics.renderComponentTooltip(this.font, this.warningTooltip(warning), mouseX, mouseY);
            } else {
                Component text = tab == TabStrip.Tab.ENERGY
                        ? (this.steamRateUnit == 0
                        ? Component.translatable("gui.meknh_core.boiler.steam_rate", this.menu.steamPerTick())
                        : Component.translatable("gui.meknh_core.boiler.steam_rate_b",
                        String.format(java.util.Locale.ROOT, "%.3f", this.menu.steamPerTick() / 1000.0D)))
                        : TabStrip.tooltip(tab, this.menu.value(BoilerMenu.DATA_REDSTONE),
                        this.menu.value(BoilerMenu.DATA_PRIVATE) == 1);
                graphics.renderTooltip(this.font, text, mouseX, mouseY);
            }
            return;
        }
        if (this.menu.isLiquid()
                && mouseX >= this.leftPos + 18 && mouseX < this.leftPos + 36
                && mouseY >= this.topPos + 10 && mouseY < this.topPos + 68) {
            graphics.renderTooltip(this.font, Component.translatable("generator.meknh_core.fuel")
                    .append(": " + this.menu.value(BoilerMenu.DATA_FUEL) + " / "
                            + this.menu.value(BoilerMenu.DATA_FUEL_CAP) + " mB"), mouseX, mouseY);
            return;
        }
        boolean solarTank = this.menu.isSolar();
        int waterX = solarTank ? 18 : 80;
        int waterY = solarTank ? 10 : 40;
        int waterH = solarTank ? 58 : 28;
        if (mouseX >= this.leftPos + waterX && mouseX < this.leftPos + waterX + 16
                && mouseY >= this.topPos + waterY && mouseY < this.topPos + waterY + waterH) {
            graphics.renderTooltip(this.font, Component.translatable("generator.meknh_core.water")
                    .append(": " + this.menu.value(BoilerMenu.DATA_WATER) + " / "
                            + this.menu.value(BoilerMenu.DATA_WATER_CAP) + " mB"), mouseX, mouseY);
            return;
        }
        if (mouseX >= this.leftPos + 140 && mouseX < this.leftPos + 158
                && mouseY >= this.topPos + 10 && mouseY < this.topPos + 68) {
            graphics.renderTooltip(this.font, Component.translatable("generator.meknh_core.steam")
                    .append(": " + this.menu.value(BoilerMenu.DATA_STEAM) + " / "
                            + this.menu.value(BoilerMenu.DATA_STEAM_CAP) + " mB"), mouseX, mouseY);
            return;
        }
        if (mouseX >= this.leftPos + RATE_X && mouseX < this.leftPos + RATE_X + RATE_WIDTH + 2
                && mouseY >= this.topPos + RATE_Y && mouseY < this.topPos + RATE_Y + RATE_HEIGHT + 2) {
            graphics.renderTooltip(this.font, Component.translatable("generator.meknh_core.temperature")
                    .append(": " + this.menu.value(BoilerMenu.DATA_TEMPERATURE) + " / "
                            + this.menu.value(BoilerMenu.DATA_TEMPERATURE_MAX) + " °C"), mouseX, mouseY);
            return;
        }
        super.renderTooltip(graphics, mouseX, mouseY);
    }
}
